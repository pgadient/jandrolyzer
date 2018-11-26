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
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.marctarnutzer.jandrolyzer.JSONDataType;
import com.marctarnutzer.jandrolyzer.JSONObject;
import com.marctarnutzer.jandrolyzer.JSONRoot;
import com.marctarnutzer.jandrolyzer.TypeEstimator;

import java.util.Map;
import java.util.Set;

public class GSONStrategy {

    public void extract(Node node, String path, Map<String, JSONRoot> jsonModels) {
        if (node instanceof MethodCallExpr) {
            if (((MethodCallExpr) node).getName().asString().equals("addProperty")) {
                jsonObjectExtraction(node, path, jsonModels);
            } else if (((MethodCallExpr) node).getName().asString().equals("toJson")) {
                pojoToJsonExtraction(node, path, jsonModels);
            }
        }
    }

    private void jsonObjectExtraction(Node node, String path, Map<String, JSONRoot> jsonModels) {
        System.out.println("Found a addProperty at path: " + path);
        // No example found as of now...
    }

    private void pojoToJsonExtraction(Node node, String path, Map<String, JSONRoot> jsonModels) {
        Expression scopeExpression = ((MethodCallExpr) node).getScope().orElse(null);
        if (scopeExpression != null) {
            String estimatedType = TypeEstimator.estimateTypeName(scopeExpression);

            if (estimatedType != null) {
                if (estimatedType.equals("com.google.gson.Gson") || estimatedType.equals("Gson") ||
                        estimatedType.equals("com.google.gson.GsonBuilder") || estimatedType.equals("GsonBuilder")) {

                    System.out.println("Found GSON / GsonBuilder node: " + node + " at path: " + path);

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
                                                    jsonModels);
                                        }

                                    } else {
                                        System.out.println("Node is not a VariableDeclarator nor FieldDeclaration" +
                                                declarationNode.getClass());
                                    }
                                } catch (Exception e) {
                                    System.out.println("Exception: " + e);
                                }
                            } else {
                                System.out.println("Not a name expr: " + arguments.get(0).getClass());
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
                               String className, Map<String, JSONRoot> jsonModels) {
        System.out.println("Analyzing fields in: " + modelPath);

        JSONRoot jsonRoot = new JSONRoot(modelPath, className, null, null);

        for (ResolvedFieldDeclaration resolvedFieldDeclaration : resolvedFieldDeclarations) {
            System.out.println("Field: " + resolvedFieldDeclaration);
            System.out.println("Type: " + resolvedFieldDeclaration.getType());

            FieldDeclaration fieldDeclaration = ((JavaParserFieldDeclaration)resolvedFieldDeclaration).getWrappedNode();

            // Don't include transient fields in JSON representation
            if (fieldDeclaration.isTransient()) {
                continue;
            }

            // Check annotation for ignore
            if (fieldDeclaration.isAnnotationDeclaration()) {
                for (AnnotationExpr annotationExpr: fieldDeclaration.getAnnotations()) {
                    System.out.println("Annotation: " + annotationExpr);
                }
            }

            if (jsonRoot.jsonObject == null) {
                jsonRoot.jsonObject = new JSONObject(JSONDataType.OBJECT, null, null);
            }

            String keyString = resolvedFieldDeclaration.getName();

            String valueType = null;

            if (resolvedFieldDeclaration.getType().isPrimitive()) {
                valueType = resolvedFieldDeclaration.getType().asPrimitive().getBoxTypeQName();
            } else if (resolvedFieldDeclaration.getType().isReferenceType()) {
                valueType = resolvedFieldDeclaration.getType().asReferenceType().getQualifiedName();
            }

            if (valueType == null) {
                continue;
            }

            JSONObject toInsert = new JSONObject(null, null, valueType);
            jsonRoot.jsonObject.linkedHashMap.put(keyString, toInsert);
        }

        if (jsonRoot.jsonObject != null && !jsonRoot.jsonObject.linkedHashMap.isEmpty()) {
            jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);
        }

    }

}
