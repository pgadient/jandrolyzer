//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 19.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.RequestStructureExtraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.marctarnutzer.jandrolyzer.*;
import com.marctarnutzer.jandrolyzer.Models.JSONDataType;
import com.marctarnutzer.jandrolyzer.Models.JSONObject;
import com.marctarnutzer.jandrolyzer.Models.JSONRoot;
import com.marctarnutzer.jandrolyzer.Models.Project;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MoshiGSONStrategy {

    private static HashSet<String> validGSONClasses = new HashSet<>(Arrays.asList(
            "com.google.gson.Gson",
            "Gson",
            "com.google.gson.GsonBuilder",
            "GsonBuilder"));

    private static HashSet<String> validMoshiClasses = new HashSet<>(Arrays.asList(
            "com.squareup.moshi.JsonAdapter",
            "JsonAdapter",
            "com.squareup.moshi.Moshi",
            "Moshi"));

    private static HashSet<String> validSimpleJSONDataTypes = new HashSet<>(Arrays.asList(
            "java.lang.String",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Integer",
            "java.lang.Boolean"));

    Project project;

    public MoshiGSONStrategy(Project project) {
        this.project = project;
    }

    private boolean isValidGSONType(String typeString) {
       return validGSONClasses.contains(typeString);
    }

    private boolean isValidMoshiType(String typeString) {
        return validMoshiClasses.contains(typeString);
    }

    private boolean isValidSimpleJSONDataType(String typeString) {
        return validSimpleJSONDataTypes.contains(typeString);
    }

    public CombinedTypeSolver combinedTypeSolver;
    private String scopeExprTypeString = null;

    public void extract(Node node, String path, Map<String, JSONRoot> jsonModels, CombinedTypeSolver combinedTypeSolver) {
        this.combinedTypeSolver = combinedTypeSolver;

        if (node instanceof MethodCallExpr) {
            if (((MethodCallExpr) node).getName().asString().equals("addProperty")) {
                jsonObjectExtraction(node, path, jsonModels);
            } else if (((MethodCallExpr) node).getName().asString().equals("toJson")) {
                pojoToJsonExtraction(node, path, jsonModels);
            } else if (((MethodCallExpr) node).getName().asString().equals("adapter")) {
                pojoToJsonExtraction(node, path, jsonModels);
            }
        }
    }

    /*
     * Extract GSON or MOSHI JSON data from ClassOrInterfaceType
     */
    public void extract(ClassOrInterfaceType classOrInterfaceType, String converterKind) {
        ResolvedType resolvedType;
        try {
            resolvedType = classOrInterfaceType.resolve();
        } catch (Exception e) {
            System.out.println("Error resolving Body type: " + e);
            return;
        }

        if (resolvedType == null || !resolvedType.isReferenceType()) {
            return;
        }

        ResolvedReferenceTypeDeclaration resolvedReferenceTypeDeclaration = resolvedType
                .asReferenceType().getTypeDeclaration();

        String modelPath = classOrInterfaceType.findCompilationUnit().get().getStorage().get()
                .getSourceRoot() + "/" + resolvedReferenceTypeDeclaration.getQualifiedName()
                .replace(".", "/") + ".java";

        analyzeFields(new HashSet<>(resolvedReferenceTypeDeclaration.getDeclaredFields()), modelPath,
                resolvedReferenceTypeDeclaration.getName(),
                project.jsonModels, null, null);
    }

    private void jsonObjectExtraction(Node node, String path, Map<String, JSONRoot> jsonModels) {
        System.out.println("Found a addProperty at path: " + path);
        // No example found as of now...
    }

    private void pojoToJsonExtraction(Node node, String path, Map<String, JSONRoot> jsonModels) {
        Expression scopeExpression = ((MethodCallExpr) node).getScope().orElse(null);
        if (scopeExpression != null) {
            String estimatedType = TypeEstimator.estimateTypeName(scopeExpression);

            scopeExprTypeString = estimatedType;

            if (estimatedType != null) {
                if (isValidGSONType(estimatedType) || isValidMoshiType(estimatedType)) {

                    if (isValidGSONType(estimatedType)) {
                        System.out.println("Found GSON / GsonBuilder node: " + node + " at path: " + path);
                    } else {
                        System.out.println("Found JsonAdapter node: " + node + " at path: " + path);
                    }

                    NodeList<Expression> arguments = ((MethodCallExpr) node).getArguments();
                    if (arguments.size() == 1) {
                        String argumentType = TypeEstimator.estimateTypeName(arguments.get(0));

                        System.out.println("Estimated argument type: " + argumentType);

                        if (argumentType != null) {
                            if (arguments.get(0) instanceof NameExpr) {
                                try {
                                    ResolvedValueDeclaration resolvedValueDeclaration =
                                            ((NameExpr)arguments.get(0)).resolve();

                                    Node declarationNode = null;

                                    if (resolvedValueDeclaration instanceof JavaParserSymbolDeclaration) {
                                        declarationNode =
                                                ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode();
                                    } else if (resolvedValueDeclaration instanceof JavaParserFieldDeclaration) {
                                        declarationNode =
                                                ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode();
                                    }

                                    System.out.println("Declaring node: " + declarationNode.toString() + " class: " +
                                            declarationNode.getClass());

                                    if (declarationNode instanceof VariableDeclarator ||
                                            declarationNode instanceof FieldDeclaration) {
                                        Type declaratorType;

                                        if (declarationNode instanceof VariableDeclarator) {
                                            declaratorType = ((VariableDeclarator) declarationNode).getType();
                                        } else {
                                            declaratorType = ((FieldDeclaration) declarationNode).getElementType();
                                        }

                                        if (declaratorType.isClassOrInterfaceType()) {
                                            String modelPath = node.findCompilationUnit().get().getStorage().get()
                                                    .getSourceRoot() + "/" + declaratorType.asClassOrInterfaceType()
                                                    .resolve().getTypeDeclaration().getQualifiedName()
                                                    .replace(".", "/") + ".java";

                                            analyzeFields(declaratorType.asClassOrInterfaceType().resolve()
                                                    .getDeclaredFields(), modelPath,
                                                    declaratorType.asClassOrInterfaceType().getName().asString(),
                                                    jsonModels, null, null);
                                        }
                                    } else {
                                        System.out.println("Node is not a VariableDeclarator nor FieldDeclaration" +
                                                declarationNode.getClass());
                                    }
                                } catch (Exception e) {
                                    System.out.println("Error while resolving NameExpr type: " + e);
                                }
                            } else if (arguments.get(0) instanceof ClassExpr) {
                                try {
                                    Type type = ((ClassExpr) arguments.get(0)).getType();
                                    System.out.println("Class expr type: " + type);

                                    if (type.isClassOrInterfaceType()) {
                                        System.out.println("Fields: " + type.asClassOrInterfaceType().resolve().getDeclaredFields());

                                        String modelPath = node.findCompilationUnit().get().getStorage().get()
                                                .getSourceRoot() + "/" + type.asClassOrInterfaceType().resolve()
                                                .getTypeDeclaration().getQualifiedName()
                                                .replace(".", "/") + ".java";

                                        analyzeFields(type.asClassOrInterfaceType().resolve().getDeclaredFields(), modelPath,
                                                type.asClassOrInterfaceType().getName().asString(),
                                                jsonModels, null, null);
                                    }
                                } catch (Exception e) {
                                    System.out.println("Error while resolving ClassExpr type: " + e);
                                }

                            } else {
                                System.out.println("Not a name nor class expr: " + arguments.get(0).getClass());
                            }
                        }
                    }
                } else {
                    System.out.println("Wrong scope type: " + estimatedType + ", node: " + node + ", path: " + path);
                }
            } else {
                System.out.println("Unable to resolve scope expression type: " + scopeExpression.toString() + ", path: " + path);
            }
        }
    }

    private void analyzeFields(Set<ResolvedFieldDeclaration> resolvedFieldDeclarations, String modelPath,
                               String className, Map<String, JSONRoot> jsonModels, JSONRoot jsonRoot,
                               JSONObject jsonObject) {
        System.out.println("Analyzing fields in: " + modelPath);

        // Check if @Expose annotation is used
        boolean exposeUsed = false;
        for (ResolvedFieldDeclaration resolvedFieldDeclaration : resolvedFieldDeclarations) {
            FieldDeclaration fieldDeclaration = ((JavaParserFieldDeclaration)resolvedFieldDeclaration).getWrappedNode();
            if (fieldDeclaration.getAnnotations().size() > 0) {
                for (AnnotationExpr annotationExpr : fieldDeclaration.getAnnotations()) {
                    if (annotationExpr.getNameAsString().equals("Expose")) {
                        exposeUsed = true;
                    }
                }
            }
        }

        if (jsonObject == null && jsonRoot == null) {
            jsonRoot = new JSONRoot(modelPath, className, null, null);
        }

        for (ResolvedFieldDeclaration resolvedFieldDeclaration : resolvedFieldDeclarations) {
            System.out.println("Field: " + resolvedFieldDeclaration);
            //System.out.println("Type: " + resolvedFieldDeclaration.getType());

            FieldDeclaration fieldDeclaration = ((JavaParserFieldDeclaration)resolvedFieldDeclaration).getWrappedNode();
            fieldDeclaration = DeclarationLocator.locate(fieldDeclaration, FieldDeclaration.class);

            System.out.println("Found fieldDeclaration: " + fieldDeclaration);

            // Don't include transient fields in JSON representation
            if (fieldDeclaration.isTransient()) {
                continue;
            }

            String keyString = resolvedFieldDeclaration.getName();

            // Check annotation for ignore
            boolean shouldSkip = true;
            if (fieldDeclaration.getAnnotations().size() > 0) {
                for (AnnotationExpr annotationExpr : fieldDeclaration.getAnnotations()) {
                    if (annotationExpr.getNameAsString().equals("SerializedName")
                            && annotationExpr.isSingleMemberAnnotationExpr()) {
                        keyString = ((SingleMemberAnnotationExpr) annotationExpr).getMemberValue()
                                .asStringLiteralExpr().asString();
                        System.out.println("Annotation rule: Changed key to: " + keyString);
                    } else if (annotationExpr.getNameAsString().equals("Json")
                            && annotationExpr.isNormalAnnotationExpr()) {
                        for (MemberValuePair memberValuePair : ((NormalAnnotationExpr) annotationExpr).getPairs()) {
                            if (memberValuePair.getName().asString().equals("name")
                                    && memberValuePair.getValue().isStringLiteralExpr()) {
                                keyString = memberValuePair.getValue().asStringLiteralExpr().getValue();
                            }
                        }
                    } else if (exposeUsed && annotationExpr.getNameAsString().equals("Expose")) {
                        if (annotationExpr.isNormalAnnotationExpr()) {
                            for (MemberValuePair memberValuePair : ((NormalAnnotationExpr) annotationExpr).getPairs()) {
                                if (memberValuePair.getName().asString().equals("serialize")
                                        && memberValuePair.getValue().isBooleanLiteralExpr()) {
                                    if (memberValuePair.getValue().asBooleanLiteralExpr().getValue()) {
                                        shouldSkip = false;
                                    }
                                }
                            }
                        } else {
                            shouldSkip = false;
                        }
                    }
                }
            }
            if (shouldSkip && exposeUsed) {
                continue;
            }

            if (jsonRoot != null && jsonRoot.jsonObject == null) {
                jsonRoot.jsonObject = new JSONObject(JSONDataType.OBJECT, null, null);
            }

            JSONObject toInsert = null;

            String fieldDeclarationTypeString = null;
            try {
                ResolvedType fieldDeclarationType = resolvedFieldDeclaration.getType();
            } catch (UnsolvedSymbolException e) {
                System.out.println("Unsolved exception: " + e);
                fieldDeclarationTypeString = e.getName();
            }

            if (fieldDeclarationTypeString != null) {
                if (fieldDeclarationTypeString.equals("String")) {
                    toInsert = new JSONObject(null, null, "java.lang.String");
                } else {
                    continue;
                }
            } else if (resolvedFieldDeclaration.getType().isPrimitive()) {
                String valueType = resolvedFieldDeclaration.getType().asPrimitive().getBoxTypeQName();

                if (valueType == null) {
                    continue;
                }

                toInsert = new JSONObject(null, null, valueType);
            } else if (resolvedFieldDeclaration.getType().isReferenceType()) {
                String resolvedReferenceTypeString = resolvedFieldDeclaration.getType().asReferenceType().
                        getQualifiedName();
                System.out.println("!TypeString" + resolvedReferenceTypeString);
                if (isValidSimpleJSONDataType(resolvedReferenceTypeString)) {
                    toInsert = new JSONObject(null, null, resolvedReferenceTypeString);
                } else {
                    try {
                        Type fieldType = fieldDeclaration.getElementType();
                        System.out.println("Field declaration: " + fieldDeclaration + ", type: " + fieldType);

                        ResolvedType resolvedType = fieldType.resolve();
                        //ResolvedType resolvedType = JavaParserFacade.get(combinedTypeSolver).convertToUsage(fieldType);
                        System.out.println("Field type: " + resolvedType.asReferenceType());
                        System.out.println("Type fields: " + resolvedType.asReferenceType().getDeclaredFields());

                        System.out.println("Field type (asClassOrInterfaceType): " +
                                (fieldType.asClassOrInterfaceType().getTypeArguments()));

                        if (TypeEstimator.extendsCollection(resolvedReferenceTypeString)) {
                            System.out.println("Its a collection type");
                            if (fieldType.asClassOrInterfaceType().getTypeArguments().isPresent() &&
                                    fieldType.asClassOrInterfaceType().getTypeArguments().get().size() == 1) {
                                System.out.println("Inserting JSON Array...");
                                toInsert = new JSONObject(JSONDataType.ARRAY, null, null);

                                Type typeArgumentType = fieldType.asClassOrInterfaceType().getTypeArguments().get().get(0);
                                //ResolvedType resolvedTypeArgType = JavaParserFacade.get(combinedTypeSolver).convertToUsage(typeArgumentType);
                                ResolvedType resolvedTypeArgType = typeArgumentType.resolve();

                                if (resolvedTypeArgType.isReferenceType()) {
                                    if (isValidSimpleJSONDataType(resolvedTypeArgType.asReferenceType().getQualifiedName())) {
                                        toInsert.arrayElementsSet.add(new JSONObject(null, null,
                                                resolvedTypeArgType.asReferenceType().getQualifiedName()));
                                    } else if (resolvedTypeArgType.asReferenceType().getTypeDeclaration().isEnum()) {
                                        toInsert.arrayElementsSet.add(new JSONObject(null, null,
                                                "java.lang.String"));
                                    } else {
                                        analyzeFields(resolvedTypeArgType.asReferenceType().getDeclaredFields(), modelPath,
                                                className, jsonModels, null, toInsert);
                                    }
                                } else if (resolvedTypeArgType.isPrimitive()) {
                                    String valueType = resolvedTypeArgType.asPrimitive().getBoxTypeQName();

                                    if (valueType == null) {
                                        continue;
                                    }

                                    toInsert.arrayElementsSet.add(new JSONObject(null, null, valueType));
                                }
                            } else if (fieldType.asClassOrInterfaceType().getTypeArguments().isPresent() &&
                                    fieldType.asClassOrInterfaceType().getTypeArguments().get().size() == 2) {

                                Type firstTypeArgumentType = fieldType.asClassOrInterfaceType().getTypeArguments().get().get(0);
                                ResolvedType resolvedFirstTypeArgumentType = firstTypeArgumentType.resolve();
                                //ResolvedType resolvedFirstTypeArgumentType = JavaParserFacade.get(combinedTypeSolver)
                                //        .convertToUsage(firstTypeArgumentType);

                                if (resolvedFirstTypeArgumentType.isReferenceType() &&
                                        resolvedFirstTypeArgumentType.asReferenceType().getQualifiedName()
                                                .equals("java.lang.String")) {
                                    System.out.println("Inserting a JSON Array for map type...");
                                    toInsert = new JSONObject(JSONDataType.ARRAY, null, null);

                                    Type secondTypeArgumentType = fieldType.asClassOrInterfaceType().getTypeArguments().get().get(1);
                                    ResolvedType resolvedSecondTypeArgumentType = secondTypeArgumentType.resolve();
                                   // ResolvedType resolvedSecondTypeArgumentType = JavaParserFacade.get(combinedTypeSolver)
                                    //        .convertToUsage(secondTypeArgumentType);

                                    if (resolvedSecondTypeArgumentType.isReferenceType()) {
                                        if (isValidSimpleJSONDataType(resolvedSecondTypeArgumentType.asReferenceType()
                                                .getQualifiedName())) {
                                            toInsert.linkedHashMap.put("", new JSONObject(null, null,
                                                    resolvedSecondTypeArgumentType.asReferenceType().getQualifiedName()));
                                        } else if (resolvedSecondTypeArgumentType.asReferenceType().getTypeDeclaration()
                                                .isEnum()) {
                                            toInsert.linkedHashMap.put("", new JSONObject(null, null,
                                                    "java.lang.String"));
                                        } else {
                                            JSONObject jsonArrayElement = new JSONObject(JSONDataType.OBJECT, null, null);
                                            toInsert.linkedHashMap.put("", jsonArrayElement);
                                            analyzeFields(resolvedSecondTypeArgumentType.asReferenceType().getDeclaredFields(),
                                                    modelPath, className, jsonModels, null, jsonArrayElement);
                                        }
                                    } else if (resolvedSecondTypeArgumentType.isPrimitive()) {
                                        String valueType = resolvedSecondTypeArgumentType.asPrimitive().getBoxTypeQName();

                                        if (valueType == null) {
                                            continue;
                                        }

                                        toInsert.linkedHashMap.put("", new JSONObject(null, null, valueType));
                                    }
                                } else {
                                    continue;
                                }
                            }
                        } else if (resolvedType.asReferenceType().getTypeDeclaration().isEnum()) {
                            System.out.println("Its an enum type");
                            toInsert = new JSONObject(null, null, "java.lang.String");
                        } else {
                            toInsert = new JSONObject(JSONDataType.OBJECT, null, null);
                            analyzeFields(resolvedType.asReferenceType().getDeclaredFields(), modelPath, className, jsonModels
                                    , null, toInsert);
                        }
                    } catch (Exception e) {
                        System.out.println("Error while analyzing field: " + e);
                    }
                }
            }

            if (toInsert == null) {
                continue;
            }

            if (jsonRoot != null) {
                System.out.println("Putting in jsonRoot... " + keyString);
                jsonRoot.jsonObject.linkedHashMap.put(keyString, toInsert);
            } else {
                System.out.println("Putting in jsonobject... " + keyString);
                jsonObject.linkedHashMap.put(keyString, toInsert);
                System.out.println("Putting in jsonobject complete.");
            }
        }

        if (jsonRoot != null && jsonRoot.jsonObject != null && !jsonRoot.jsonObject.linkedHashMap.isEmpty()) {
            jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);

            System.out.println("Added new jsonModels entry.");

            if (isValidGSONType(scopeExprTypeString)) {
                jsonRoot.library = "com.google.code.gson";
            } else {
                jsonRoot.library = "com.squareup.moshi";
            }
        }

    }

}
