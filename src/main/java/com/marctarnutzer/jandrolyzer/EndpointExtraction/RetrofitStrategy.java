//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 14.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserInterfaceDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.marctarnutzer.jandrolyzer.DeclarationLocator;
import com.marctarnutzer.jandrolyzer.Project;
import com.marctarnutzer.jandrolyzer.RequestStructureExtraction.MoshiGSONStrategy;

import java.util.LinkedList;
import java.util.List;

public class RetrofitStrategy {

    Project project;
    APIURLStrategy apiurlStrategy;
    public MoshiGSONStrategy moshiGSONStrategy;

    public RetrofitStrategy(Project project, APIURLStrategy apiurlStrategy, MoshiGSONStrategy moshiGSONStrategy) {
        this.project = project;
        this.apiurlStrategy = apiurlStrategy;
        this.moshiGSONStrategy = moshiGSONStrategy;
    }

    public boolean extract(MethodCallExpr methodCallExpr) {
        if (!(methodCallExpr.getScope().isPresent() && methodCallExpr.getScope().get().isNameExpr())) {
            return false;
        }

        NameExpr scope = methodCallExpr.getScope().get().asNameExpr();

        ResolvedValueDeclaration resolvedValueDeclaration;
        try {
            resolvedValueDeclaration = scope.resolve();
        } catch (Exception e) {
            System.out.println("Retrofit create() methodCallExpr resolve error: " + e);
            return false;
        }

        if (resolvedValueDeclaration == null) {
            return false;
        }

        System.out.println("Retrofit RVD: " + resolvedValueDeclaration);

        if (!(resolvedValueDeclaration.getType().isReferenceType()
                && (resolvedValueDeclaration.getType().asReferenceType().getQualifiedName().equals("retrofit2.Retrofit")
                || resolvedValueDeclaration.getType().asReferenceType().getQualifiedName().equals("retrofit.Retrofit")))) {
            return false;
        }

        String converterKind = null;
        List<String> baseUrls = null;
        if (resolvedValueDeclaration instanceof JavaParserSymbolDeclaration) {
            Node declarationNode = ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode();
            if (declarationNode instanceof VariableDeclarator) {
                VariableDeclarator variableDeclarator = ((VariableDeclarator) declarationNode);
                if (variableDeclarator.getInitializer().isPresent()) {
                    converterKind = getConverterKind(variableDeclarator.getInitializer().get());
                    baseUrls = getBaseURLs(variableDeclarator.getInitializer().get());
                    System.out.println("ConverterKind: " + converterKind + ", Base URLs: " + baseUrls);
                }
            }
        }

        if (methodCallExpr.getArguments().size() != 1) {
            return false;
        }

        if (!methodCallExpr.getArgument(0).isClassExpr()) {
            return false;
        }

        if (methodCallExpr.getArgument(0).isClassExpr() && methodCallExpr.getArgument(0)
                .asClassExpr().getType().isClassOrInterfaceType()) {
            ResolvedReferenceType resolvedReferenceType;
            try {
                resolvedReferenceType = methodCallExpr.getArgument(0)
                        .asClassExpr().getType().asClassOrInterfaceType().resolve();
            } catch (Exception e) {
                System.out.println("Error: unable to resolve Retrofit interface: " + e);
                return false;
            }

            if (resolvedReferenceType == null || !resolvedReferenceType.getTypeDeclaration().isInterface()) {
                return false;
            }

            ClassOrInterfaceDeclaration interfaceDeclaration = ((JavaParserInterfaceDeclaration) resolvedReferenceType
                    .getTypeDeclaration().asInterface()).getWrappedNode();

            interfaceDeclaration = DeclarationLocator.locate(interfaceDeclaration, ClassOrInterfaceDeclaration.class);

            if (interfaceDeclaration == null) {
                return false;
            }

            return extractInterfaceAPIURLs(interfaceDeclaration, baseUrls, converterKind);
        }

        return false;
    }

    private boolean extractInterfaceAPIURLs(ClassOrInterfaceDeclaration interfaceDeclaration, List<String> baseUrls,
                                            String converterKind) {
        List<MethodDeclaration> methodDeclarations = interfaceDeclaration.findAll(MethodDeclaration.class);

        List<String> apiEndpoints = new LinkedList<>();
        for (MethodDeclaration methodDeclaration : methodDeclarations) {
            if (methodDeclaration.getAnnotations().isEmpty()) {
                continue;
            }

            String apiUrl = null;
            String httpMethod = null;
            for (AnnotationExpr annotationExpr : methodDeclaration.getAnnotations()) {
                if (!annotationExpr.isSingleMemberAnnotationExpr()) {
                    continue;
                }

                if (annotationExpr.getName().asString().equals("GET")
                        || annotationExpr.getName().asString().equals("POST")
                        || annotationExpr.getName().asString().equals("PATCH")
                        || annotationExpr.getName().asString().equals("DELETE")) {
                    Expression memberValueExpr = annotationExpr.asSingleMemberAnnotationExpr().getMemberValue();
                    if (memberValueExpr.isStringLiteralExpr()) {
                        apiUrl = memberValueExpr.asStringLiteralExpr().getValue();
                        httpMethod = annotationExpr.getNameAsString();
                    }
                }
            }

            if (apiUrl == null || httpMethod == null) {
                continue;
            }

            for (Parameter parameter : methodDeclaration.getParameters()) {
                if (parameter.getAnnotations().size() == 1 && parameter.getAnnotation(0).isSingleMemberAnnotationExpr()) {

                    if (parameter.getAnnotation(0).asSingleMemberAnnotationExpr().getName().asString()
                            .equals("Path")) {
                        String toReplace = parameter.getAnnotation(0).asSingleMemberAnnotationExpr()
                                .getMemberValue().asStringLiteralExpr().getValue();

                        String validType = isValidType(parameter.getType());
                        if (validType == null) {
                            continue;
                        }

                        String typeString = "<" + validType + ">";

                        apiUrl = apiUrl.replaceAll("\\{" + toReplace + "}", typeString);

                    } else if (parameter.getAnnotation(0).asSingleMemberAnnotationExpr().getName().asString()
                            .equals("Query")) {
                        String validType = isValidType(parameter.getType());
                        if (validType == null) {
                            continue;
                        }

                        String typeString = "<" + validType + ">";

                        String toInsert = parameter.getAnnotation(0).asSingleMemberAnnotationExpr()
                                .getMemberValue().asStringLiteralExpr().getValue();

                        apiUrl = apiUrl + "&" + toInsert + "=" + typeString;
                    }
                } else if (parameter.getAnnotations().size() == 1 && parameter.getAnnotation(0)
                        .isMarkerAnnotationExpr()) {
                    if (converterKind != null && parameter.getAnnotation(0).asMarkerAnnotationExpr().getName()
                            .asString().equals("Body")) {
                        // Extract JSON structure of this API call

                        moshiGSONStrategy.extract(parameter.getType().asClassOrInterfaceType(), converterKind);
                    }
                }
            }

            if (apiUrl.split("&")[0].length() < apiUrl.split("\\?")[0].length()) {
                apiUrl = apiUrl.replaceFirst("&", "\\?");
            }

            apiEndpoints.add(apiUrl);
        }

        List<String> fullURLs = new LinkedList<>();
        if (baseUrls != null) {
            for (String baseURL : baseUrls) {
                for (String endpoint : apiEndpoints) {
                    if (apiurlStrategy.isValidURL(endpoint)) {
                        fullURLs.add(endpoint);
                    } else {
                        fullURLs.add(baseURL + endpoint);
                    }
                }
            }
        } else {
            fullURLs.addAll(apiEndpoints);
        }

        System.out.println("URLs to check: " + fullURLs);

        boolean extractedValidAPIURL = false;
        for (String url : fullURLs) {
            extractedValidAPIURL = apiurlStrategy.extract(url, project) || extractedValidAPIURL;
        }

        return extractedValidAPIURL;
    }

    private String isValidType(Type type) {
        if (type.isPrimitiveType()) {
            return type.asPrimitiveType().toBoxedType().asString();
        } else if (type.isClassOrInterfaceType()) {
            if (type.asClassOrInterfaceType().getName().asString().equals("String")
                    || type.asClassOrInterfaceType().getName().asString().equals("Double")
                    || type.asClassOrInterfaceType().getName().asString().equals("Float")
                    || type.asClassOrInterfaceType().getName().asString().equals("Integer")
                    || type.asClassOrInterfaceType().getName().asString().equals("Boolean")) {
                return type.asClassOrInterfaceType().getName().asString();
            }
        }

        return null;
    }

    /*
     * Returns kind of ConverterFactory used and null if no ConverterFactory specified
     */
    private String getConverterKind(Expression scope) {
        if (!scope.isMethodCallExpr()) {
            return null;
        }

        if (scope.asMethodCallExpr().getName().asString().equals("addConverterFactory")) {
            for (Expression arg : scope.asMethodCallExpr().getArguments()) {
                if (arg.isMethodCallExpr() && arg.asMethodCallExpr().getScope().isPresent()
                        && arg.asMethodCallExpr().getScope().get().isNameExpr()) {
                    if (arg.asMethodCallExpr().getScope().get().asNameExpr().getName().asString()
                            .equals("GsonConverterFactory")) {
                        return "GSON";
                    } else if (arg.asMethodCallExpr().getScope().get().asNameExpr().getName().asString()
                            .equals("MoshiConverterFactory")) {
                        return "MOSHI";
                    }
                }
            }
        }

        if (scope.asMethodCallExpr().getScope().isPresent()) {
            return getConverterKind(scope.asMethodCallExpr().getScope().get());
        }

        return null;
    }

    private List<String> getBaseURLs(Expression scope) {
        if (!scope.isMethodCallExpr()) {
            return null;
        }

        if (scope.asMethodCallExpr().getName().asString().equals("baseUrl")) {
            if (scope.asMethodCallExpr().getArguments().size() != 1) {
                return null;
            }

            List<String> baseUrls = ExpressionValueExtraction.getExpressionValue(scope.asMethodCallExpr().getArgument(0));
            if (baseUrls == null || baseUrls.isEmpty()) {
                return null;
            }

            return baseUrls;
        }

        if (scope.asMethodCallExpr().getScope().isPresent()) {
            return getBaseURLs(scope.asMethodCallExpr().getScope().get());
        }

        return null;
    }

}
