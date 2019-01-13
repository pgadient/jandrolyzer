//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 12.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.marctarnutzer.jandrolyzer.Project;
import com.marctarnutzer.jandrolyzer.TypeEstimator;
import com.marctarnutzer.jandrolyzer.Utils;
import okhttp3.HttpUrl;

import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;

public class OkHttpStrategy {

    Project project;
    APIURLStrategy apiurlStrategy;

    public OkHttpStrategy(Project project, APIURLStrategy apiurlStrategy) {
        this.project = project;
        this.apiurlStrategy = apiurlStrategy;
    }

    /*
     * Extract API URLs from OkHttp Builder MethodCallExpr
     *
     * Return true if a valid URL was found, false otherwise
     */
    public boolean extract(MethodCallExpr methodCallExpr) {
        if (!methodCallExpr.getScope().isPresent()) {
            return false;
        }

        String estimatedType = TypeEstimator.estimateTypeName(methodCallExpr);

        System.out.println("Estimated build() type: " + estimatedType);

        if (estimatedType.equals("okhttp3.HttpUrl")
                || estimatedType.equals("okhttp.HttpUrl")
                || estimatedType.equals("HttpUrl")) {
            List<String> stringsToCheck = extractExpressionValue(methodCallExpr.getScope().get());

            if (stringsToCheck == null) {
                return false;
            }

            for (int i = 0; i < stringsToCheck.size(); i++) {
                stringsToCheck.set(i, stringsToCheck.get(i).replaceFirst("&", "?"));
            }

            System.out.println("HttpUrl to check: " + stringsToCheck);

            boolean foundValidURLs = false;
            for (String extractedURL : stringsToCheck) {
                foundValidURLs = apiurlStrategy.extract(extractedURL, project) || foundValidURLs;
            }

            return foundValidURLs;
        }

        return false;
    }

    /*
     * Extract API URLs from OkHttp Builder VariableDeclarator
     *
     * Return true if a valid URL was found, false otherwise
     */
    public boolean extract(VariableDeclarator variableDeclarator) {
        if (variableDeclarator.getType().isClassOrInterfaceType() && variableDeclarator.getType()
                .asClassOrInterfaceType().getName().asString().equals("Builder") && variableDeclarator.getType()
                .asClassOrInterfaceType().getScope().isPresent() && variableDeclarator.getType()
                .asClassOrInterfaceType().getScope().get().isClassOrInterfaceType() && variableDeclarator.getType()
                .asClassOrInterfaceType().getScope().get().asClassOrInterfaceType().getName().asString().equals("HttpUrl")) {
            System.out.println("Found OkHttp VariableDeclarator: " + variableDeclarator);

            List<String> prePath = null;
            if (variableDeclarator.getInitializer().isPresent()) {
                if (variableDeclarator.getInitializer().get().isMethodCallExpr()) {
                    prePath = extractExpressionValue(variableDeclarator.getInitializer().get());
                }
            }

            Node containingNode = Utils.getParentClassOrMethod(variableDeclarator);
            List<String> foundValues = new LinkedList<>();
            if (containingNode instanceof MethodDeclaration) {
                checkMethodForOkHttpBuilderNameExpr(containingNode, variableDeclarator,
                        variableDeclarator.getName().asString(), foundValues);
            } else if (containingNode instanceof ClassOrInterfaceDeclaration) {
                if (variableDeclarator.getParentNode().isPresent()
                        && variableDeclarator.getParentNode().get() instanceof FieldDeclaration) {
                    Node fieldNode = variableDeclarator.getParentNode().get();

                    List<MethodDeclaration> methodDeclarations = new LinkedList<>();
                    if (((FieldDeclaration) fieldNode).isPublic()) {
                        for (CompilationUnit cu : project.compilationUnits) {
                            methodDeclarations.addAll(cu.findAll(MethodDeclaration.class));
                        }
                    } else {
                        methodDeclarations = containingNode.findAll(MethodDeclaration.class);
                    }

                    for (MethodDeclaration methodDeclaration : methodDeclarations) {
                        checkMethodForOkHttpBuilderNameExpr(methodDeclaration, fieldNode,
                                variableDeclarator.getName().asString(), foundValues);
                    }
                }
            }

            List<String> extractedUrls = new LinkedList<>();
            if (prePath != null && !prePath.isEmpty()) {
                for (String pp : prePath) {
                    for (String fv : foundValues) {
                        extractedUrls.add(pp + fv);
                    }
                }
            } else {
                extractedUrls = foundValues;
            }

            for (int i = 0; i < extractedUrls.size(); i++) {
                extractedUrls.set(i, extractedUrls.get(i).replaceFirst("&", "?"));
            }

            System.out.println("Extracted OkHttp URLs: " + extractedUrls);

            // TODO: Add string values to URL collection if valid

            boolean foundValidURLs = false;
            for (String extractedURL : extractedUrls) {
                foundValidURLs = apiurlStrategy.extract(extractedURL, project) || foundValidURLs;
            }

            return foundValidURLs;
        }

        return false;
    }

    private void checkMethodForOkHttpBuilderNameExpr(Node node, Node variableOrFieldNode, String variableName,
                                                             List<String> foundValues) {
        if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = (MethodCallExpr) node;

            boolean passedAsArg = false;
            if (methodCallExpr.getArguments().isNonEmpty()) {
                int argPosition = -1;
                for (int i = 0; i < methodCallExpr.getArguments().size(); i++) {
                    Expression expression = methodCallExpr.getArguments().get(i);
                    if (expression.isNameExpr()) {
                        ResolvedValueDeclaration resolvedValueDeclaration = null;
                        try {
                            resolvedValueDeclaration = expression.asNameExpr().resolve();
                        } catch (Exception e) {
                            System.out.println("Error OkHttpStrategy resolving NameExpr: " + e);
                        }

                        if (resolvedValueDeclaration != null && resolvedValueDeclaration instanceof JavaParserSymbolDeclaration
                                && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                                .equals(variableOrFieldNode)) {
                            argPosition = i;
                        }
                    }
                }

                if (argPosition > -1) {
                    System.out.println("Builder is passed as argument");

                    ResolvedMethodDeclaration resolvedMethodDeclaration = null;
                    try {
                        resolvedMethodDeclaration = methodCallExpr.resolve();
                    } catch (Exception e) {
                        System.out.println("Error OkHttpStrategy resolving methodCallExpr: " + e);
                    }

                    if (resolvedMethodDeclaration != null) {
                        MethodDeclaration methodDeclaration = ((JavaParserMethodDeclaration) resolvedMethodDeclaration).getWrappedNode();
                        Parameter parameter = methodDeclaration.getParameter(argPosition);
                        System.out.println("Found parameter: " + parameter);

                        checkMethodForOkHttpBuilderNameExpr(methodDeclaration, parameter,
                                parameter.getName().asString(), foundValues);
                        passedAsArg = true;
                    }
                }
            }

            if (methodCallExpr.getParentNode().isPresent()
                    && !(methodCallExpr.getParentNode().get() instanceof MethodCallExpr && !passedAsArg)) {
                NameExpr leftmostScope = getleftmostScope(methodCallExpr);

                if (leftmostScope != null) {
                    boolean foundPotentialMatch = false;
                    if (variableOrFieldNode instanceof VariableDeclarator) {
                        VariableDeclarator variableDeclarator = (VariableDeclarator) variableOrFieldNode;
                        if (leftmostScope.getName().asString().equals(variableDeclarator.getName().asString())) {
                            System.out.println("Found potential match: " + node);

                            foundPotentialMatch = true;
                        }
                    } else if (variableOrFieldNode instanceof FieldDeclaration) {
                        FieldDeclaration fieldDeclaration = (FieldDeclaration) variableOrFieldNode;
                        VariableDeclarator foundVD = null;
                        for (VariableDeclarator variableDeclarator : fieldDeclaration.getVariables()) {
                            if (variableDeclarator.getName().asString().equals(variableName)) {
                                foundVD = variableDeclarator;
                            }
                        }

                        if (foundVD != null) {
                            if (leftmostScope.getName().asString().equals(foundVD.getName().asString())) {
                                System.out.println("Potential potential field match + " + node);

                                foundPotentialMatch = true;
                            }
                        }
                    } else if (variableOrFieldNode instanceof Parameter) {
                        Parameter parameter = (Parameter) variableOrFieldNode;
                        if (leftmostScope.getName().asString().equals(parameter.getNameAsString())) {
                            System.out.println("Found potential parameter match: " + node);

                            foundPotentialMatch = true;
                        }
                    }

                    if (foundPotentialMatch) {
                        ResolvedValueDeclaration resolvedValueDeclaration = null;
                        try {
                            resolvedValueDeclaration = leftmostScope.resolve();
                        } catch (Exception e) {
                            System.out.println("Error OkHttpStrategy resolvedValueDeclaration: " + e);
                        }

                        boolean matchingType = false;
                        if (resolvedValueDeclaration != null) {
                            if ((resolvedValueDeclaration instanceof JavaParserSymbolDeclaration
                                    && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                                    .equals(variableOrFieldNode))
                                    || (resolvedValueDeclaration instanceof JavaParserFieldDeclaration
                                    && ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode()
                                    .equals(variableOrFieldNode))
                                    || (resolvedValueDeclaration instanceof JavaParserParameterDeclaration)
                                    && ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode()
                                    .equals(variableOrFieldNode)) {
                                matchingType = true;
                            }
                        }

                        if (matchingType) {
                            List<String> expressionValues = extractExpressionValue(methodCallExpr);

                            if (expressionValues != null) {
                                if (foundValues.isEmpty()) {
                                    foundValues.addAll(expressionValues);
                                } else {
                                    List<String> appendedValues = new LinkedList<>();
                                    for (String foundValue : foundValues) {
                                        for (String expressionValue : expressionValues) {
                                            appendedValues.add(foundValue + expressionValue);
                                        }
                                    }
                                    foundValues.clear();
                                    foundValues.addAll(appendedValues);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (Node child : node.getChildNodes()) {
            checkMethodForOkHttpBuilderNameExpr(child, variableOrFieldNode, variableName, foundValues);
        }
    }

    /*
     * Returns leftmost scope if it is a NameExpr, else null
     */
    private NameExpr getleftmostScope(MethodCallExpr methodCallExpr) {
        if (methodCallExpr.getScope().isPresent()) {
            if (methodCallExpr.getScope().get().isMethodCallExpr()) {
                return getleftmostScope(methodCallExpr.getScope().get().asMethodCallExpr());
            } else if (methodCallExpr.getScope().get().isNameExpr()) {
                return methodCallExpr.getScope().get().asNameExpr();
            }
        }

        return null;
    }

    private List<String> extractExpressionValue(Expression expression) {
        System.out.println("Checking expression " + expression);

        if (expression.isMethodCallExpr()) {
            MethodCallExpr methodCallExpr = ((MethodCallExpr) expression);

            List<String> preString = new LinkedList<>();
            if (methodCallExpr.getScope().isPresent()) {
                List<String> ps = extractExpressionValue(methodCallExpr.getScope().get());
                if (ps != null) {
                    preString.addAll(ps);
                }
            }

            List<String> toReturn = new LinkedList<>();
            switch (methodCallExpr.getName().asString()) {
                case "newBuilder":
                    if (methodCallExpr.getScope().isPresent() && methodCallExpr.getScope().get().isMethodCallExpr()
                            && methodCallExpr.getScope().get().asMethodCallExpr().getName().asString().equals("parse")
                            && methodCallExpr.getScope().get().asMethodCallExpr().getArguments().size() == 1) {
                        List<String> argValues = ExpressionValueExtraction.getExpressionValue(
                                methodCallExpr.getScope().get().asMethodCallExpr().getArgument(0));

                        if (preString.isEmpty()) {
                            toReturn = argValues;
                        } else {
                            for (String ps : preString) {
                                for (String av : argValues) {
                                    toReturn.add(ps + av);
                                }
                            }
                        }
                    } else {
                        return null;
                    }
                    break;
                case "addQueryParameter": case "addEncodedQueryParameter":
                    if (methodCallExpr.getArguments().size() != 2) {
                        return null;
                    }

                    List<String> arg0Values = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(0));
                    List<String> arg1Values = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(1));

                    System.out.println("Arg 0 values: " + arg0Values + ", arg 1 values: " + arg1Values);

                    List<String> queryPairs = new LinkedList<>();
                    for (String queryName : arg0Values) {
                        for (String queryValue : arg1Values) {
                            if (queryName == null || queryValue == null) {
                                continue;
                            }

                            if (methodCallExpr.getName().asString().equals("addQueryParameter")) {
                                HttpUrl httpUrl = new HttpUrl.Builder().scheme("http").host("somehost.com")
                                        .addQueryParameter(queryName, queryValue).build();
                                String encodedQuery = httpUrl.encodedQuery();

                                queryPairs.add("&" + encodedQuery);
                            } else {
                                queryPairs.add("&" + queryName + "=" + queryValue);
                            }
                        }
                    }

                    if (preString.isEmpty()) {
                        toReturn = queryPairs;
                    } else {
                        for (String ps : preString) {
                            for (String tr : queryPairs) {
                                toReturn.add(ps + tr);
                            }
                        }
                    }

                    break;
                case "scheme": case "host": case "port": case "addPathSegment": case "addPathSegments":
                case "addEncodedPathSegment": case "addEncodedPathSegments": case "fragment": case "encodedFragment":
                    if (methodCallExpr.getArguments().size() != 1) {
                        return null;
                    }

                    List<String> argValues = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(0));

                    System.out.println("Arg values: " + argValues);

                    List<String> expressionValues = new LinkedList<>();
                    if (methodCallExpr.getName().asString().equals("scheme")) {
                        for (String argValue : argValues) {
                            expressionValues.add(argValue + "://");
                        }
                    } else if (methodCallExpr.getName().asString().equals("port")) {
                        for (String argValue : argValues) {
                            expressionValues.add(":" + argValue);
                        }
                    } else if (methodCallExpr.getName().asString().equals("addPathSegment")) {
                        for (String argValue : argValues) {
                            HttpUrl httpUrl = new HttpUrl.Builder().scheme("http").host("somehost.com")
                                    .addPathSegment(argValue).build();
                            argValue = httpUrl.encodedPath();
                            expressionValues.add(argValue);
                        }
                    } else if (methodCallExpr.getName().asString().equals("addPathSegments")) {
                        for (String argValue : argValues) {
                            HttpUrl httpUrl = new HttpUrl.Builder().scheme("http").host("somehost.com")
                                    .addPathSegments(argValue).build();
                            argValue = httpUrl.encodedPath();
                            expressionValues.add(argValue);
                        }
                    } else if (methodCallExpr.getName().asString().equals("addEncodedPathSegment")
                            || methodCallExpr.getName().asString().equals("addEncodedPathSegments")) {
                        for (String argValue : argValues) {
                            expressionValues.add("/" + argValue);
                        }
                    } else if (methodCallExpr.getName().asString().equals("fragment")) {
                        for (String argValue : argValues) {
                            HttpUrl httpUrl = new HttpUrl.Builder().scheme("http").host("somehost.com")
                                    .fragment(argValue).build();
                            argValue = httpUrl.encodedFragment();
                            expressionValues.add("#" + argValue);
                        }
                    } else if (methodCallExpr.getName().asString().equals("encodedFragment")) {
                        for (String argValue : argValues) {
                            expressionValues.add("#" + argValue);
                        }
                    } else {
                        expressionValues.addAll(argValues);
                    }

                    if (preString.isEmpty()) {
                        toReturn = expressionValues;
                    } else {
                        for (String ps : preString) {
                            for (String ev : expressionValues) {
                                toReturn.add(ps + ev);
                            }
                        }
                    }
            }

            System.out.println("OkHttp returning: " + toReturn);

            if (toReturn.isEmpty()) {
                return null;
            } else {
                return toReturn;
            }
        }


        return null;
    }

}