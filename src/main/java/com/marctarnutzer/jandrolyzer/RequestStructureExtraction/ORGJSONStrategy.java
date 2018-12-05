//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.RequestStructureExtraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.marctarnutzer.jandrolyzer.*;

import java.util.Map;

public class ORGJSONStrategy {

    private String methodOrConstructor;
    private String classOrInterface;
    private String scopeName;
    private String keyString;
    private String valueType;
    private Object valueObject;

    // Extract org.json relevant information from node
    public void extract(Node node, String path, Map<String, JSONRoot> jsonModels) {
        methodOrConstructor = null;
        classOrInterface = null;
        scopeName = null;
        keyString = null;
        valueObject = null;
        valueObject = null;

        if (node instanceof MethodCallExpr) {
            Expression scopeExpr = ((MethodCallExpr) node).getScope().orElse(null);
            if (scopeExpr != null) {
                String estimatedType = TypeEstimator.estimateTypeName(scopeExpr);

                if (estimatedType != null && (estimatedType.equals("JSONObject") ||
                        estimatedType.equals("org.json.JSONObject"))) {
                    System.out.println("Found type: " + estimatedType + " for expression: " + scopeExpr.toString());
                    System.out.println("Path: " + path);

                    scopeName = scopeExpr.toString();

                    // Get method and class name from declaration if possible
                    if (scopeExpr instanceof NameExpr) {
                        try {
                            ResolvedValueDeclaration resolvedValueDeclaration= ((NameExpr)scopeExpr).resolve();
                            Node declarationNode = ((JavaParserSymbolDeclaration)resolvedValueDeclaration).getWrappedNode();
                            fillMethodAndClassInfo(declarationNode);
                        } catch (Exception e) {
                            System.out.println("Exception: " + e);
                        }
                    }

                    if (methodOrConstructor == null && classOrInterface == null) {
                        fillMethodAndClassInfo(node);
                    }

                    NodeList<Expression> arguments = ((MethodCallExpr) node).getArguments();
                    if (arguments.size() == 2) {
                        if (arguments.get(0) instanceof StringLiteralExpr) {
                            System.out.println("Found string literal expression: " + ((StringLiteralExpr)arguments.get(0)).asString());
                            keyString = ((StringLiteralExpr)arguments.get(0)).asString();

                            if (arguments.get(1) instanceof NameExpr) {
                                System.out.println("Found name expression: " + ((NameExpr)arguments.get(1)).getName());
                                String argumentType = TypeEstimator.estimateTypeName(arguments.get(1));
                                System.out.println("Argument type: " + argumentType);

                                System.out.println("Parent: " + arguments.get(1).getParentNodeForChildren().getClass());

                                if (arguments.get(1).getParentNodeForChildren() instanceof PrimitiveType) {
                                    System.out.println("Its a primitive type");
                                }

                                valueType = argumentType;

                            } else if (arguments.get(1) instanceof IntegerLiteralExpr) {
                                Integer found = ((IntegerLiteralExpr)arguments.get(1)).asInt();
                                valueObject = found;
                            } else if (arguments.get(1) instanceof StringLiteralExpr) {
                                String found = ((StringLiteralExpr)arguments.get(1)).asString();
                                valueObject = found;
                            } else if (arguments.get(1) instanceof CharLiteralExpr) {
                                String found = String.valueOf(((CharLiteralExpr)arguments.get(1)).asChar());
                                valueObject = found;
                            } else if (arguments.get(1) instanceof BooleanLiteralExpr) {
                                Boolean found = ((BooleanLiteralExpr)arguments.get(1)).getValue();
                                valueObject = found;
                            } else if (arguments.get(1) instanceof DoubleLiteralExpr) {
                                Double found = ((DoubleLiteralExpr)arguments.get(1)).asDouble();
                                valueObject = found;
                            } else if (arguments.get(1) instanceof LongLiteralExpr) {
                                Double found = (double)((LongLiteralExpr)arguments.get(1)).asLong();
                                valueObject = found;
                            } else if (arguments.get(1) instanceof NullLiteralExpr) {
                                valueType = "NULL";
                            } else {
                                // TODO: Add option to allow unknown expressions or just add in as string? let's see...
                                System.out.println("Not an accepted expression: " + arguments.get(1).toString()
                                    + ", class: " + arguments.get(1).getClass());
                                String argumentType = TypeEstimator.estimateTypeName(arguments.get(1));
                                System.out.println("Estimated type: " + argumentType);
                                return;
                            }

                            addToJSONMap(path, jsonModels);
                        }
                    }
                } else {
                    //System.out.println("Type did not match, type: " + estimatedType + " for expression: "
                    //        + scopeExpr.toString());
                }
            }
        }
    }

    private void addToJSONMap(String path, Map<String, JSONRoot> jsonModels) {
        JSONRoot jsonRoot = new JSONRoot(path, classOrInterface, methodOrConstructor, scopeName);
        if (jsonModels.containsKey(jsonRoot.getIdentifier())) {
            System.out.println("JSON object already exists, id: " + jsonRoot.getIdentifier());
            JSONObject jsonObject = new JSONObject(null, valueObject, valueType);
            jsonModels.get(jsonRoot.getIdentifier()).jsonObject.linkedHashMap.put(keyString, jsonObject);
        } else {
            System.out.println("Creating new JSON object...");
            jsonRoot.jsonObject = new JSONObject(JSONDataType.OBJECT, null, null);
            JSONObject toInsert = new JSONObject(null, valueObject, valueType);
            jsonRoot.jsonObject.linkedHashMap.put(keyString, toInsert);
            jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);
        }

        jsonRoot.library = "org.json";

        System.out.println("Added to JSONMap");
    }

    private void fillMethodAndClassInfo(Node node) {
        Node parentNode = Utils.getParentClassOrMethod(node);
        if (parentNode instanceof MethodDeclaration || parentNode instanceof ConstructorDeclaration) {
            Node grandparentNode = Utils.getParentClassOrMethod(parentNode);
            if (grandparentNode instanceof ClassOrInterfaceDeclaration) {
                if (parentNode instanceof MethodDeclaration) {
                    methodOrConstructor = ((MethodDeclaration)parentNode).getName().asString();
                } else {
                    methodOrConstructor = ((ConstructorDeclaration)parentNode).getName().asString();
                }
                classOrInterface = ((ClassOrInterfaceDeclaration) grandparentNode).getName().asString();

                System.out.println("Found method/constructor: " + methodOrConstructor +
                        " and class/interface: " + classOrInterface);
            }
        } else if (parentNode instanceof ClassOrInterfaceDeclaration) {
            System.out.println("Found only class/interface: " + ((ClassOrInterfaceDeclaration) parentNode).getName().asString());
            classOrInterface = ((ClassOrInterfaceDeclaration) parentNode).getName().asString();
        } else {
            System.out.println("No info about node found");
        }
    }
}
