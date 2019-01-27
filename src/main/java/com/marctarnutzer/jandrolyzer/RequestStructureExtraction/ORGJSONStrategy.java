//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.RequestStructureExtraction;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.marctarnutzer.jandrolyzer.*;
import com.marctarnutzer.jandrolyzer.EndpointExtraction.ExpressionValueExtraction;
import com.marctarnutzer.jandrolyzer.Models.JSONDataType;
import com.marctarnutzer.jandrolyzer.Models.JSONObject;
import com.marctarnutzer.jandrolyzer.Models.JSONRoot;
import com.marctarnutzer.jandrolyzer.Models.Project;
import sun.awt.image.ImageWatched;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ORGJSONStrategy {

    private Project project;

    public List<String> extract(VariableDeclarator variableDeclarator, Project project) {
        this.project = project;

        if (variableDeclarator.getType().isClassOrInterfaceType() && variableDeclarator.getType()
                .asClassOrInterfaceType().getName().asString().equals("JSONObject")) {
            System.out.println("Found JSONObject VariableDeclarator: " + variableDeclarator);

            List<String> initStrings = new LinkedList<>();
            if (variableDeclarator.getInitializer().isPresent()
                    && variableDeclarator.getInitializer().get().isObjectCreationExpr()
                    && variableDeclarator.getInitializer().get().asObjectCreationExpr().getArguments().size() == 1) {
                Expression arg = variableDeclarator.getInitializer().get().asObjectCreationExpr().getArgument(0);
                String typeString = TypeEstimator.estimateTypeName(arg);

                if (typeString != null && typeString.equals("java.lang.String")) {
                    List<String> preStrings = ExpressionValueExtraction.getExpressionValue(arg);

                    if (preStrings != null && !preStrings.isEmpty()) {
                        for (String preString : preStrings) {
                            preString = Utils.removeEscapeSequencesFrom(preString);
                            try {
                                org.json.JSONObject jsonObject = new org.json.JSONObject(preString);
                                initStrings.add(jsonObject.toString());
                            } catch (Exception e) {
                                System.out.println("Error while initializing new JSONObject: " + e);
                            }
                        }
                    }
                }
            } else if (variableDeclarator.getInitializer().isPresent()
                    && variableDeclarator.getInitializer().get().isMethodCallExpr()) {
                MethodCallExpr methodCallExpr = variableDeclarator.getInitializer().get().asMethodCallExpr();

                List<List<String>> jsonStringLists = new LinkedList<>();
                jsonStringLists.add(new LinkedList<>());
                getValuesFromMethodCallExpr(methodCallExpr, jsonStringLists);

                for (List<String> stringList : jsonStringLists) {
                    initStrings.addAll(stringList);
                }
            }

            System.out.println("Init strings: " + initStrings);

            Node containingNode = Utils.getParentClassOrMethod(variableDeclarator);

            List<String> toCheck = new LinkedList<>();
            if (containingNode instanceof MethodDeclaration) {
                List<List<String>> jsonStringLists = new LinkedList<>();
                jsonStringLists.add(initStrings);
                extractJSONStringsFromMethod(containingNode, variableDeclarator, jsonStringLists);
                for (List<String> jsonStringList : jsonStringLists) {
                    toCheck.addAll(jsonStringList);
                }
            } else if (containingNode instanceof ClassOrInterfaceDeclaration) {
                System.out.println("Searching methods for JSONObject field: " + variableDeclarator);

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
                        List<List<String>> jsonStringLists = new LinkedList<>();
                        jsonStringLists.add(initStrings);
                        extractJSONStringsFromMethod(methodDeclaration, fieldNode, jsonStringLists);

                        for (List<String> jsonStringList : jsonStringLists) {
                            toCheck.addAll(jsonStringList);
                        }
                    }
                }
            }

            System.out.println("org.json strings to check: " + toCheck);

            return toCheck;
        }

        return null;
    }

    private void extractJSONStringsFromMethod(Node node, Node variableDeclarationNode,
                                              List<List<String>> jsonStringLists) {
        if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = ((MethodCallExpr) node);
            if (methodCallExpr.getNameAsString().equals("put") && methodCallExpr.getArguments().size() == 2) {
                System.out.println("Put methodCallExpr detected: " + methodCallExpr);

                Node nonMCEScope = getScope(methodCallExpr);
                if (nonMCEScope != null && nonMCEScope instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) nonMCEScope;
                    try {
                        ResolvedValueDeclaration resolvedValueDeclaration = nameExpr.resolve();

                        System.out.println("ResolvedValueDeclaration: " + resolvedValueDeclaration);

                        if (((resolvedValueDeclaration instanceof JavaParserSymbolDeclaration)
                                && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                                .equals(variableDeclarationNode))
                                || ((resolvedValueDeclaration instanceof JavaParserFieldDeclaration)
                                && ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode()
                                .equals(variableDeclarationNode))
                                || ((resolvedValueDeclaration instanceof JavaParserParameterDeclaration)
                                && ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode()
                                .equals(variableDeclarationNode))) {
                            System.out.println("Found valid put() MethodCallExpr: " + resolvedValueDeclaration);

                            List<String> arg1Values = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(0));
                            List<String> arg2Values = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(1));

                            System.out.println("Arg1Values: " + arg1Values + ", arg2Values: " + arg2Values);

                            String typeString = TypeEstimator.estimateTypeName(methodCallExpr.getArgument(1));

                            List<String> toAdd = new LinkedList<>();
                            if (jsonStringLists.get(jsonStringLists.size() - 1).isEmpty()) {
                                for (String arg1Value : arg1Values) {
                                    for (String arg2Value : arg2Values) {
                                        try {
                                            org.json.JSONObject jsonObject = new org.json.JSONObject();

                                            if (typeString != null) {
                                                insertKeyValuePair(arg1Value, arg2Value, jsonObject, typeString);
                                            } else {
                                                jsonObject.put(arg1Value, arg2Value);
                                            }

                                            toAdd.add(jsonObject.toString());
                                        } catch (Exception e) {
                                            System.out.println("JSON error: " + e);
                                            continue;
                                        }
                                    }
                                }
                            } else {
                                for (String preString : jsonStringLists.get(jsonStringLists.size() - 1)) {
                                    for (String arg1Value : arg1Values) {
                                        for (String arg2Value : arg2Values) {
                                            try {
                                                System.out.println("PreString: " + preString);
                                                org.json.JSONObject jsonObject = new org.json.JSONObject(preString);

                                                if (typeString != null) {
                                                    insertKeyValuePair(arg1Value, arg2Value, jsonObject, typeString);
                                                } else {
                                                    jsonObject.put(arg1Value, arg2Value);
                                                }

                                                System.out.println("Adding: " + jsonObject.toString());

                                                toAdd.add(jsonObject.toString());
                                            } catch (Exception e) {
                                                System.out.println("JSON error: " + e);
                                                continue;
                                            }
                                        }
                                    }
                                }
                            }

                            System.out.println("toAdd: " + toAdd);

                            jsonStringLists.get(jsonStringLists.size() - 1).clear();
                            jsonStringLists.get(jsonStringLists.size() - 1).addAll(toAdd);
                            //org.json.JSONObject jsonObject = jsonObjects.get(jsonObjects.size() - 1);
                        }
                    } catch (Exception e) {
                        System.out.println("Error resolving NameExpr: " + e);
                    }
                }
            } else if (methodCallExpr.getArguments().size() > 0) {
                int position = -1;
                for (Expression arg : methodCallExpr.getArguments()) {
                    position++;
                    if (variableDeclarationNode instanceof VariableDeclarator) {
                        if (arg.isNameExpr() && arg.asNameExpr().getName().asString()
                                .equals(((VariableDeclarator)variableDeclarationNode).getName().asString())) {
                            System.out.println("NameExpr possibly used as argument: " + methodCallExpr);
                            ResolvedValueDeclaration resolvedValueDeclaration;
                            try {
                                resolvedValueDeclaration = arg.asNameExpr().resolve();
                            } catch (Exception e) {
                                System.out.println("Error resolving argument of MCE: " + e);
                                continue;
                            }

                            if (((resolvedValueDeclaration instanceof JavaParserSymbolDeclaration)
                                    && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                                    .equals(variableDeclarationNode))
                                    || ((resolvedValueDeclaration instanceof JavaParserFieldDeclaration)
                                    && ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode()
                                    .equals(variableDeclarationNode))
                                    || ((resolvedValueDeclaration instanceof JavaParserParameterDeclaration)
                                    && ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode()
                                    .equals(variableDeclarationNode))) {
                                System.out.println("Found MCE with nameExpr as argument: " + methodCallExpr);

                                ResolvedMethodDeclaration resolvedMethodDeclaration;
                                try {
                                    resolvedMethodDeclaration = methodCallExpr.resolve();
                                } catch (Exception e) {
                                    System.out.println("Error resolving MCE: " + e);
                                    continue;
                                }

                                if (resolvedMethodDeclaration == null) {
                                    continue;
                                }

                                MethodDeclaration methodDeclaration =
                                        ((JavaParserMethodDeclaration) resolvedMethodDeclaration).getWrappedNode();
                                methodDeclaration = DeclarationLocator.locate(methodDeclaration, MethodDeclaration.class);

                                if (methodDeclaration == null) {
                                    continue;
                                }

                                System.out.println("Found MethodDeclaration: " + methodDeclaration);

                                extractJSONStringsFromMethod(methodDeclaration,
                                        methodDeclaration.getParameter(position), jsonStringLists);
                            }
                        }
                    }
                }
            }
        } else if (node instanceof AssignExpr) {
            System.out.println("Assign expr detected: " + node);

            Expression value = ((AssignExpr) node).getValue();
            Expression target = ((AssignExpr) node).getTarget();
            if (target.isNameExpr() && ((value.isObjectCreationExpr()
                    && value.asObjectCreationExpr().getType().isClassOrInterfaceType()
                    && value.asObjectCreationExpr().getType().asClassOrInterfaceType().getName().asString()
                    .equals("JSONObject")) || value.isMethodCallExpr())) {
                try {
                    ResolvedValueDeclaration resolvedValueDeclaration = target.asNameExpr().resolve();
                    if (((resolvedValueDeclaration instanceof JavaParserSymbolDeclaration)
                            && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                            .equals(variableDeclarationNode))
                            || ((resolvedValueDeclaration instanceof JavaParserFieldDeclaration)
                            && ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode()
                            .equals(variableDeclarationNode))
                            || ((resolvedValueDeclaration instanceof JavaParserParameterDeclaration)
                            && ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode()
                            .equals(variableDeclarationNode))) {
                        System.out.println("Assign operation to NameExpr detected: " + node);

                        if (value.isObjectCreationExpr()) {
                            if (!jsonStringLists.get(jsonStringLists.size() - 1).isEmpty()) {
                                jsonStringLists.add(new LinkedList<>());
                            }

                            List<String> prevStrings = new LinkedList<>();
                            for (List<String> prevStringList : jsonStringLists) {
                                prevStrings.addAll(prevStringList);
                            }
                            jsonStringLists.get(jsonStringLists.size() - 1).addAll(prevStrings);

                            if (value.asObjectCreationExpr().getArguments().size() == 1) {
                                Expression arg = value.asObjectCreationExpr().getArgument(0);
                                String typeString = TypeEstimator.estimateTypeName(arg);

                                if (typeString != null && typeString.equals("java.lang.String")) {
                                    List<String> preValues = ExpressionValueExtraction.getExpressionValue(arg);

                                    if (preValues != null && !preValues.isEmpty()) {
                                        for (String preValue : preValues) {
                                            preValue = Utils.removeEscapeSequencesFrom(preValue);
                                            System.out.println("Preval: " + preValue);
                                            try {
                                                org.json.JSONObject jsonObject = new org.json.JSONObject(preValue);
                                                System.out.println("ToAdd: " + jsonObject.toString());
                                                jsonStringLists.get(jsonStringLists.size() - 1).add(jsonObject.toString());
                                            } catch (Exception e) {
                                                System.out.println("Exception initializing new JSONObject: " + e);
                                                continue;
                                            }
                                        }
                                    }
                                }
                            }

                            System.out.println("Initialized new list: " + jsonStringLists.get(jsonStringLists.size() - 1));
                        } else if (value.isMethodCallExpr()) {
                            System.out.println("Its a mce");

                            if (!jsonStringLists.get(jsonStringLists.size() - 1).isEmpty()) {
                                jsonStringLists.add(new LinkedList<>());
                            }

                            List<String> prevStrings = new LinkedList<>();
                            for (List<String> prevStringList : jsonStringLists) {
                                prevStrings.addAll(prevStringList);
                            }
                            jsonStringLists.get(jsonStringLists.size() - 1).addAll(prevStrings);

                            getValuesFromMethodCallExpr((MethodCallExpr) value, jsonStringLists);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error resolving NameExpr: " + e);
                }
            }
        } else if (node instanceof Statement && ((Statement) node).isReturnStmt()
                && ((Statement) node).asReturnStmt().getExpression().isPresent()
                && ((Statement) node).asReturnStmt().getExpression().get().isNameExpr()) {
            System.out.println("Found nameExpr return statement: " + node);
            ResolvedValueDeclaration resolvedValueDeclaration;
            try {
                resolvedValueDeclaration = ((Statement) node).asReturnStmt().getExpression().get().asNameExpr().resolve();
                if (((resolvedValueDeclaration instanceof JavaParserSymbolDeclaration)
                        && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                        .equals(variableDeclarationNode))
                        || ((resolvedValueDeclaration instanceof JavaParserFieldDeclaration)
                        && ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode()
                        .equals(variableDeclarationNode))
                        || ((resolvedValueDeclaration instanceof JavaParserParameterDeclaration)
                        && ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode()
                        .equals(variableDeclarationNode))) {
                    System.out.println("Found matching nameExpr as return statement: " + node);

                    if (!jsonStringLists.get(jsonStringLists.size() - 1).isEmpty()) {
                        jsonStringLists.add(new LinkedList<>());
                    }

                    List<String> prevStrings = new LinkedList<>();
                    for (List<String> prevStringList : jsonStringLists) {
                        prevStrings.addAll(prevStringList);
                    }
                    jsonStringLists.get(jsonStringLists.size() - 1).addAll(prevStrings);
                }
            } catch (Exception e) {
                System.out.println("Error resolving return stmt: " + e);
            }
        }

        for (Node child : node.getChildNodes()) {
            extractJSONStringsFromMethod(child, variableDeclarationNode, jsonStringLists);
        }
    }

    private void getValuesFromMethodCallExpr(MethodCallExpr methodCallExpr, List<List<String>> jsonStringLists) {
        try {
            ResolvedMethodDeclaration resolvedMethodDeclaration = methodCallExpr.asMethodCallExpr().resolve();

            MethodDeclaration methodDeclaration =
                    ((JavaParserMethodDeclaration) resolvedMethodDeclaration).getWrappedNode();
            methodDeclaration = DeclarationLocator.locate(methodDeclaration, MethodDeclaration.class);

            if (methodDeclaration != null) {
                System.out.println("Found methodDeclaration: " + methodDeclaration);

                List<ReturnStmt> returnStatements = methodDeclaration.findAll(ReturnStmt.class);
                Node declarationNode = null;
                for (ReturnStmt returnStmt : returnStatements) {
                    if (!(returnStmt.getExpression().isPresent()
                            && returnStmt.getExpression().get().isNameExpr())) {
                        continue;
                    }

                    try {
                        ResolvedValueDeclaration rvd =
                                returnStmt.getExpression().get().asNameExpr().resolve();

                        if (!(rvd.getType().isReferenceType() && rvd.getType().asReferenceType()
                                .getQualifiedName().equals("org.json.JSONObject"))) {
                            continue;
                        }

                        System.out.println("ResolvedValueDeclaration: " + rvd);

                        if (rvd instanceof JavaParserSymbolDeclaration) {
                            System.out.println("Its a JavaParserSymbolDeclaration");
                            declarationNode = ((JavaParserSymbolDeclaration) rvd).getWrappedNode();
                            break;
                        } else if (rvd instanceof JavaParserFieldDeclaration) {
                            System.out.println("Its a JavaParserFieldDeclaration");
                            declarationNode = ((JavaParserSymbolDeclaration) rvd).getWrappedNode();
                            break;
                        } else if (rvd instanceof JavaParserParameterDeclaration) {
                            System.out.println("Its a JavaParserParameterDeclaration");
                            declarationNode = ((JavaParserSymbolDeclaration) rvd).getWrappedNode();
                            break;
                        }

                    } catch (Exception e) {
                        System.out.println("Error resolving nameExpr: " + e);
                    }
                }

                if (declarationNode != null) {
                    System.out.println("Extracting: " + declarationNode);

                    if (declarationNode instanceof VariableDeclarator) {
                        System.out.println("DeclarationNode is VariableDeclarator");

                        List<String> methodStrings = extract((VariableDeclarator) declarationNode, project);

                        if (methodStrings != null && !methodStrings.isEmpty()) {
                            System.out.println("Adding methodStrings");

                            jsonStringLists.get(jsonStringLists.size() - 1).addAll(methodStrings);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error resolving MCE: " + e);
        }
    }

    private void insertKeyValuePair(String arg1Value, String arg2Value, org.json.JSONObject jsonObject, String typeString) {
        switch (typeString) {
            case "java.lang.String":
                if (arg2Value.toLowerCase().equals("null")) {
                    jsonObject.put(arg1Value, org.json.JSONObject.NULL);
                } else {
                    jsonObject.put(arg1Value, arg2Value);
                }
                break;
            case "java.lang.Double":
                Double d = Double.valueOf(arg2Value);
                jsonObject.put(arg1Value, d);
                break;
            case "java.lang.Float":
                Float f = Float.valueOf(arg2Value);
                jsonObject.put(arg1Value, f);
                break;
            case "java.lang.Integer":
                Integer i = Integer.valueOf(arg2Value);
                jsonObject.put(arg1Value, i);
                break;
            case "java.lang.Boolean":
                Boolean b = Boolean.valueOf(arg2Value);
                jsonObject.put(arg1Value, b);
                break;
            default:
                if (arg2Value != null && arg2Value.toLowerCase().equals("null")) {
                    jsonObject.put(arg1Value, org.json.JSONObject.NULL);
                } else {
                    jsonObject.put(arg1Value, arg2Value);
                }
        }
    }

    private Node getScope(MethodCallExpr methodCallExpr) {
        if (!methodCallExpr.getScope().isPresent()) {
            return null;
        }

        if (methodCallExpr.getScope().get().isMethodCallExpr()) {
            return getScope(methodCallExpr.getScope().get().asMethodCallExpr());
        } else {
            return methodCallExpr.getScope().get();
        }
    }
}
