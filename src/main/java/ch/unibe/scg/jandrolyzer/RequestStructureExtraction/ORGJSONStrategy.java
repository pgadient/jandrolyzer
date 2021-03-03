//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package ch.unibe.scg.jandrolyzer.RequestStructureExtraction;

import ch.unibe.scg.jandrolyzer.DeclarationLocator;
import ch.unibe.scg.jandrolyzer.EndpointExtraction.ExpressionValueExtraction;
import ch.unibe.scg.jandrolyzer.Main;
import ch.unibe.scg.jandrolyzer.Models.Project;
import ch.unibe.scg.jandrolyzer.TypeEstimator;
import ch.unibe.scg.jandrolyzer.Utils;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

public class ORGJSONStrategy {

    private Project project;

    public List<String> extract(VariableDeclarator variableDeclarator, Project project, int depthLevel) {
        this.project = project;

        if (variableDeclarator.getType().isClassOrInterfaceType()) {
            String objectKind = null;
            if (variableDeclarator.getType().asClassOrInterfaceType().getName().asString().equals("JSONObject")) {
                System.out.println("Found JSONObject VariableDeclarator: " + variableDeclarator);
                objectKind = "JSONObject";
            } else if (variableDeclarator.getType().asClassOrInterfaceType().getName().asString().equals("JSONArray")) {
                System.out.println("Found JSONArray VariableDeclarator: " + variableDeclarator);
                objectKind = "JSONArray";
            } else {
                return null;
            }

            List<String> initStrings = new LinkedList<>();
            if (variableDeclarator.getInitializer().isPresent()
                    && variableDeclarator.getInitializer().get().isObjectCreationExpr()
                    && variableDeclarator.getInitializer().get().asObjectCreationExpr().getArguments().size() == 1) {
                Expression arg = variableDeclarator.getInitializer().get().asObjectCreationExpr().getArgument(0);
                String typeString = TypeEstimator.estimateTypeName(arg);

                if (typeString != null && typeString.equals("java.lang.String")) {
                    List<String> preStrings = ExpressionValueExtraction.getExpressionValue(arg, null, 0);

                    if (preStrings != null && !preStrings.isEmpty()) {
                        for (String preString : preStrings) {
                            preString = Utils.removeEscapeSequencesFrom(preString);
                            try {
                                if (objectKind.equals("JSONObject")) {
                                    org.json.JSONObject jsonObject = new org.json.JSONObject(preString);
                                    initStrings.add(jsonObject.toString());
                                } else {
                                    org.json.JSONArray jsonArray = new org.json.JSONArray(preString);
                                    initStrings.add(jsonArray.toString());
                                }
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
                getValuesFromMethodCallExpr(methodCallExpr, jsonStringLists, depthLevel);

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
                extractJSONStringsFromMethod(containingNode, variableDeclarator, jsonStringLists, objectKind, depthLevel);
                for (List<String> jsonStringList : jsonStringLists) {
                    toCheck.addAll(jsonStringList);
                }
            } else if (containingNode instanceof ClassOrInterfaceDeclaration) {
                System.out.println("Searching methods for JSONObject/JSONArray field: " + variableDeclarator);

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
                        extractJSONStringsFromMethod(methodDeclaration, fieldNode, jsonStringLists, objectKind, depthLevel);

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
                                              List<List<String>> jsonStringLists, String objectKind, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return;
        }
        depthLevel++;

        if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = ((MethodCallExpr) node);
            if (methodCallExpr.getNameAsString().equals("put") && methodCallExpr.getArguments().size() == 1
                    || methodCallExpr.getNameAsString().equals("put") && methodCallExpr.getArguments().size() == 2) {
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

                            List<String> arg1Values;
                            List<String> arg2Values = null;
                            String argKind = null;
                            if (objectKind.equals("JSONObject")) {
                                arg1Values = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(0), null, 0);
                                arg2Values = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(1), null, 0);

                                if (arg2Values == null) {
                                    if (methodCallExpr.getArgument(1).isNameExpr()) {
                                        NameExpr argNameExpr = methodCallExpr.getArgument(1).asNameExpr();
                                        List<String> preStrings = new LinkedList<>();
                                        getValuesFromNameExpr(argNameExpr, preStrings, depthLevel);
                                        if (!preStrings.isEmpty()) {
                                            arg2Values = preStrings;
                                            if (preStrings.get(0).startsWith("{")) {
                                                argKind = "JSONObject";
                                            } else {
                                                argKind = "JSONArray";
                                            }
                                        }
                                    }
                                }
                            } else {
                                arg1Values = ExpressionValueExtraction.getExpressionValue(methodCallExpr.getArgument(0), null, 0);

                                if (arg1Values == null) {
                                    if (methodCallExpr.getArgument(0).isNameExpr()) {
                                        NameExpr argNameExpr = methodCallExpr.getArgument(0).asNameExpr();
                                        List<String> preStrings = new LinkedList<>();
                                        getValuesFromNameExpr(argNameExpr, preStrings, depthLevel);
                                        if (!preStrings.isEmpty()) {
                                            arg1Values = preStrings;
                                            if (preStrings.get(0).startsWith("{")) {
                                                argKind = "JSONObject";
                                            } else {
                                                argKind = "JSONArray";
                                            }
                                        }
                                    }
                                }
                            }

                            System.out.println("Arg1Values: " + arg1Values + ", arg2Values: " + arg2Values);

                            String typeString;
                            if (arg2Values != null) {
                                typeString = TypeEstimator.estimateTypeName(methodCallExpr.getArgument(1));
                            } else {
                                typeString = TypeEstimator.estimateTypeName(methodCallExpr.getArgument(0));
                            }

                            List<String> toAdd = new LinkedList<>();
                            if (jsonStringLists.get(jsonStringLists.size() - 1).isEmpty()) {
                                if (objectKind.equals("JSONObject")) {
                                    for (String arg1Value : arg1Values) {
                                        for (String arg2Value : arg2Values) {
                                            try {
                                                org.json.JSONObject jsonObject = new org.json.JSONObject();

                                                if (argKind != null) {
                                                    if (argKind.equals("JSONObject")) {
                                                        JSONObject toInsert = new JSONObject(arg2Value);
                                                        jsonObject.put(arg1Value, toInsert);
                                                    } else {
                                                        JSONArray toInsert = new JSONArray(arg2Value);
                                                        jsonObject.put(arg1Value, toInsert);
                                                    }
                                                } else if (typeString != null) {
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
                                    for (String arg1Value : arg1Values) {
                                        try {
                                            JSONArray jsonArray = new JSONArray();

                                            if (argKind != null) {
                                                if (argKind.equals("JSONObject")) {
                                                    JSONObject toInsert = new JSONObject(arg1Value);
                                                    jsonArray.put(toInsert);
                                                } else {
                                                    JSONArray toInsert = new JSONArray(arg1Value);
                                                    jsonArray.put(toInsert);
                                                }
                                            } else if (typeString != null) {
                                                insertJSONArrayValue(arg1Value, jsonArray, typeString);
                                            } else {
                                                jsonArray.put(arg1Value);
                                            }

                                            toAdd.add(jsonArray.toString());
                                        } catch (Exception e) {
                                            System.out.println("JSON error: " + e);
                                            continue;
                                        }
                                    }
                                }
                            } else {
                                if (objectKind.equals("JSONObject")) {
                                    for (String preString : jsonStringLists.get(jsonStringLists.size() - 1)) {
                                        for (String arg1Value : arg1Values) {
                                            for (String arg2Value : arg2Values) {
                                                try {
                                                    System.out.println("PreString: " + preString);
                                                    org.json.JSONObject jsonObject = new org.json.JSONObject(preString);

                                                    if (argKind != null) {
                                                        if (argKind.equals("JSONObject")) {
                                                            JSONObject toInsert = new JSONObject(arg2Value);
                                                            jsonObject.put(arg1Value, toInsert);
                                                        } else {
                                                            JSONArray toInsert = new JSONArray(arg2Value);
                                                            jsonObject.put(arg1Value, toInsert);
                                                        }
                                                    } else if (typeString != null) {
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
                                } else {
                                    for (String preString : jsonStringLists.get(jsonStringLists.size() - 1)) {
                                        for (String arg1Value : arg1Values) {
                                            try {
                                                System.out.println("PreString: " + preString);
                                                JSONArray jsonArray = new JSONArray(preString);

                                                if (argKind != null) {
                                                    if (argKind.equals("JSONObject")) {
                                                        JSONObject toInsert = new JSONObject(arg1Value);
                                                        jsonArray.put(toInsert);
                                                    } else {
                                                        JSONArray toInsert = new JSONArray(arg1Value);
                                                        jsonArray.put(toInsert);
                                                    }
                                                } else if (typeString != null) {
                                                    insertJSONArrayValue(arg1Value, jsonArray, typeString);
                                                } else {
                                                    jsonArray.put(arg1Value);
                                                }

                                                System.out.println("Adding: " + jsonArray.toString());

                                                toAdd.add(jsonArray.toString());
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

                                if (resolvedMethodDeclaration == null || !(resolvedMethodDeclaration instanceof JavaParserMethodDeclaration)) {
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
                                        methodDeclaration.getParameter(position), jsonStringLists, objectKind, depthLevel);
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
                    .equals(objectKind)) || value.isMethodCallExpr())) {
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
                                    List<String> preValues = ExpressionValueExtraction.getExpressionValue(arg, null, 0);

                                    if (preValues != null && !preValues.isEmpty()) {
                                        for (String preValue : preValues) {
                                            preValue = Utils.removeEscapeSequencesFrom(preValue);
                                            System.out.println("Preval: " + preValue);
                                            try {
                                                if (objectKind.equals("JSONObject")) {
                                                    org.json.JSONObject jsonObject = new org.json.JSONObject(preValue);
                                                    System.out.println("ToAdd: " + jsonObject.toString());
                                                    jsonStringLists.get(jsonStringLists.size() - 1).add(jsonObject.toString());
                                                } else {
                                                    org.json.JSONArray jsonArray = new org.json.JSONArray(preValue);
                                                    System.out.println("ToAdd: " + jsonArray.toString());
                                                    jsonStringLists.get(jsonStringLists.size() - 1).add(jsonArray.toString());
                                                }
                                            } catch (Exception e) {
                                                System.out.println("Exception initializing new JSONObject/JSONArray: " + e);
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

                            getValuesFromMethodCallExpr((MethodCallExpr) value, jsonStringLists, depthLevel);
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
            extractJSONStringsFromMethod(child, variableDeclarationNode, jsonStringLists, objectKind, depthLevel - 1);
        }
    }

    private void getValuesFromNameExpr(NameExpr nameExpr, List<String> jsonStringList, int depthLevel) {
        try {
            ResolvedValueDeclaration resolvedValueDeclaration = nameExpr.resolve();
            if (resolvedValueDeclaration.getType().isReferenceType()) {
                if (resolvedValueDeclaration.getType().asReferenceType().getQualifiedName().equals("org.json.JSONArray")
                        || resolvedValueDeclaration.getType().asReferenceType().getQualifiedName().equals("org.json.JSONObject")) {
                    System.out.println("JSONObject / JSONArray as value");
                    Node declarationNode = null;
                    if (resolvedValueDeclaration instanceof JavaParserSymbolDeclaration) {
                        declarationNode = ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode();
                    } else if (resolvedValueDeclaration instanceof JavaParserFieldDeclaration) {
                        declarationNode = ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode();
                    } else if (resolvedValueDeclaration instanceof JavaParserParameterDeclaration) {
                        declarationNode = ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode();
                    }

                    if (declarationNode == null || !(declarationNode instanceof VariableDeclarator)) {
                        return;
                    }

                    List<String> preStrings = extract((VariableDeclarator) declarationNode, project, depthLevel);

                    if (preStrings != null && !preStrings.isEmpty()) {
                        System.out.println("JSONArray / JSONObject strings: " + preStrings);
                        jsonStringList.addAll(preStrings);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error resolving NameExpr: " + e);
        }
    }

    private void getValuesFromMethodCallExpr(MethodCallExpr methodCallExpr, List<List<String>> jsonStringLists, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return;
        }
        depthLevel++;

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

                        List<String> methodStrings = extract((VariableDeclarator) declarationNode, project, depthLevel);

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

    private void insertJSONArrayValue(String arg1Value, org.json.JSONArray jsonArray, String typeString) {
        switch (typeString) {
            case "java.lang.String":
                if (arg1Value.toLowerCase().equals("null")) {
                    jsonArray.put(JSONObject.NULL);
                } else {
                    jsonArray.put(arg1Value);
                }
                break;
            case "java.lang.Double":
                Double d = Double.valueOf(arg1Value);
                jsonArray.put(d);
                break;
            case "java.lang.Float":
                Float f = Float.valueOf(arg1Value);
                jsonArray.put(f);
                break;
            case "java.lang.Integer":
                Integer i = Integer.valueOf(arg1Value);
                jsonArray.put(i);
                break;
            case "java.lang.Boolean":
                Boolean b = Boolean.valueOf(arg1Value);
                jsonArray.put(b);
                break;
            default:
                if (arg1Value != null && arg1Value.toLowerCase().equals("null")) {
                    jsonArray.put(org.json.JSONObject.NULL);
                } else {
                    jsonArray.put(arg1Value);
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
