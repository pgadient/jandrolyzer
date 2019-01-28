//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 11.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionFieldDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionMethodDeclaration;
import com.marctarnutzer.jandrolyzer.AssignmentLocator;
import com.marctarnutzer.jandrolyzer.DeclarationLocator;
import com.marctarnutzer.jandrolyzer.Models.Project;
import com.marctarnutzer.jandrolyzer.TypeEstimator;
import com.marctarnutzer.jandrolyzer.Utils;

import java.util.*;

public class ExpressionValueExtraction {

    static Project project;

    /*
     * Extract API URLs from java.net.URL ObjectCreationExpr
     *
     * Returns list of value Strings or null if an error occurred
     */
    public static List<String> extractURLValue(ObjectCreationExpr objectCreationExpr) {
        ResolvedType resolvedType;
        try {
            resolvedType = objectCreationExpr.calculateResolvedType();
        } catch (Exception e) {
            System.out.println("Error calculating resolved type: " + e);
            return null;
        }

        if (!(resolvedType.isReferenceType() && resolvedType.asReferenceType().getQualifiedName().equals("java.net.URL"))) {
            return null;
        }

        if (objectCreationExpr.getArguments().isEmpty()) {
            return null;
        }

        List<String> toReturn = new LinkedList<>();

        if (objectCreationExpr.getArguments().size() == 1) {
            toReturn = getExpressionValue(objectCreationExpr.getArgument(0));
        } else if (objectCreationExpr.getArguments().size() == 2) {
            List<String> context = getExpressionValue(objectCreationExpr.getArgument(0));
            List<String> spec = getExpressionValue(objectCreationExpr.getArgument(1));

            if (context == null || context.isEmpty()
                    || spec == null || spec.isEmpty()) {
                return null;
            }

            for (String c : context) {
                for (String s : spec) {
                    toReturn.add(c + s);
                }
            }
        } else if (objectCreationExpr.getArguments().size() == 3) {
            List<String> protocols = getExpressionValue(objectCreationExpr.getArgument(0));
            List<String> host = getExpressionValue(objectCreationExpr.getArgument(1));
            List<String> file = getExpressionValue(objectCreationExpr.getArgument(2));

            if (protocols == null || protocols.isEmpty()
                    || host == null || host.isEmpty() ||
                    file == null || file.isEmpty()) {
                return null;
            }

            for (String p : protocols) {
                for (String h : host) {
                    for (String f : file) {
                        toReturn.add(p + "://" + h + f);
                    }
                }
            }
        } else if (objectCreationExpr.getArguments().size() == 4) {
            List<String> protocols = getExpressionValue(objectCreationExpr.getArgument(0));
            List<String> host = getExpressionValue(objectCreationExpr.getArgument(1));
            List<String> port = getExpressionValue(objectCreationExpr.getArgument(2));
            List<String> file = getExpressionValue(objectCreationExpr.getArgument(3));

            if (protocols == null || protocols.isEmpty()
                    || host == null || host.isEmpty()
                    || file == null || file.isEmpty()
                    || port == null || port.isEmpty()) {
                return null;
            }

            for (String p : protocols) {
                for (String h : host) {
                    for (String po : port) {
                        for (String f : file) {
                            toReturn.add(p + "://" + h + ":" + po + f);
                        }
                    }
                }
            }
        }

        if (toReturn.isEmpty()) {
            return null;
        } else {
            return toReturn;
        }
    }

    public static List<String> extractStringConcatValue(MethodCallExpr methodCallExpr) {
        if (methodCallExpr.getArguments().size() != 1 || !methodCallExpr.getScope().isPresent()) {
            return null;
        }

        Expression scope = methodCallExpr.getScope().get();
        List<String> toCheck = getExpressionValue(methodCallExpr.getArgument(0));

        if (toCheck == null || !(scope.isMethodCallExpr() || scope.isNameExpr() || scope.isFieldAccessExpr())) {
            return null;
        }

        List<String> prePath = null;
        if (scope.isMethodCallExpr()) {
            prePath = extractStringConcatValue((MethodCallExpr) scope);
        } else if (scope.isNameExpr() || scope.isFieldAccessExpr()) {
            prePath = getExpressionValue(scope);
        }

        List<String> toReturn = new LinkedList<>();
        if (prePath != null) {
            for (String s : toCheck) {
                for (String p : prePath) {
                    toReturn.add(p + s);
                }
            }
        } else {
            return null;
        }

        return toReturn;
    }

    public static List<String> extractStringBuilderValue(VariableDeclarator variableDeclarator, Project project) {
        Node containingNode = Utils.getParentClassOrMethod(variableDeclarator);

        List<String> potentialApiURLs = new LinkedList<>();
        if (containingNode instanceof MethodDeclaration) {
            List<MethodCallExpr> methodCallExprs = containingNode.findAll(MethodCallExpr.class);
            potentialApiURLs = reconstructStringBuilderStringsIn(methodCallExprs, variableDeclarator);
        } else if (containingNode instanceof ClassOrInterfaceDeclaration) {
            if (variableDeclarator.getParentNode().isPresent() && variableDeclarator.getParentNode().get()
                    instanceof FieldDeclaration) {
                Node fieldNode = variableDeclarator.getParentNode().get();

                List<MethodDeclaration> methodDeclarations = new LinkedList<>();
                if (((FieldDeclaration) fieldNode).isPublic()) {
                    for (CompilationUnit cu : project.compilationUnits) {
                        methodDeclarations.addAll(cu.findAll(MethodDeclaration.class));
                    }
                } else {
                    methodDeclarations = containingNode.findAll(MethodDeclaration.class);
                }

                //List<MethodDeclaration> methodDeclarations = containingNode.findAll(MethodDeclaration.class);
                for (MethodDeclaration methodDeclaration : methodDeclarations) {
                    List<MethodCallExpr> methodCallExprs = methodDeclaration.findAll(MethodCallExpr.class);
                    potentialApiURLs.addAll(reconstructStringBuilderStringsIn(methodCallExprs, fieldNode));
                }
            }
        }

        if (!potentialApiURLs.isEmpty()) {
            return potentialApiURLs;
        }

        return null;
    }

    public static List<String> reconstructStringBuilderStringsIn(List<MethodCallExpr> methodCallExprs,
                                                           Node variableOrFieldNode) {
        List<String> potentialApiURLs = new LinkedList<>();
        Stack<List<String>> chainedValuesStack = new Stack<>();
        for (MethodCallExpr methodCallExpr : methodCallExprs) {
            if (!methodCallExpr.getName().asString().equals("append")) {
                /*
                 * Check if StringBuilder is passed to another method and append potential String values
                 */
                int parameterNbr = 0;
                for (Expression expression : methodCallExpr.getArguments()) {
                    ResolvedValueDeclaration resolvedValueDeclaration = null;
                    if (expression.isFieldAccessExpr()) {
                        try {
                            resolvedValueDeclaration = expression.asFieldAccessExpr().resolve();
                        } catch (Exception e) {
                            continue;
                        }
                    } else if (expression.isNameExpr()) {
                        try {
                            resolvedValueDeclaration = expression.asNameExpr().resolve();
                        } catch (Exception e) {
                            continue;
                        }
                    } else {
                        continue;
                    }

                    if (resolvedValueDeclaration == null) {
                        continue;
                    }

                    if (!(resolvedValueDeclaration.getType().isReferenceType()
                            && resolvedValueDeclaration.getType().asReferenceType().getQualifiedName()
                            .equals("java.lang.StringBuilder"))) {
                        continue;
                    }

                    if (((resolvedValueDeclaration instanceof JavaParserSymbolDeclaration)
                            && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                            .equals(variableOrFieldNode)) || ((resolvedValueDeclaration instanceof JavaParserFieldDeclaration)
                            && ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode().equals(variableOrFieldNode))) {
                        System.out.println("Found valid StringBuilder parameter in method call: " + methodCallExpr);

                        MethodDeclaration methodDeclaration = null;
                        try {
                            ResolvedMethodDeclaration resolvedMethodDeclaration = methodCallExpr.resolve();
                            methodDeclaration = ((JavaParserMethodDeclaration) resolvedMethodDeclaration).getWrappedNode();
                        } catch (Exception e) {
                            continue;
                        }

                        if (methodDeclaration == null) {
                            continue;
                        }

                        List<MethodCallExpr> mMethodCallExpr = methodDeclaration.findAll(MethodCallExpr.class);
                        List<String> sbValues = reconstructStringBuilderStringsIn(mMethodCallExpr, methodDeclaration.getParameter(parameterNbr));

                        if (sbValues.isEmpty()) {
                            continue;
                        }

                        List<String> appendedPotentialAPIURLs = new ArrayList<>();
                        if (!potentialApiURLs.isEmpty()) {
                            for (String potentialAPIURL : potentialApiURLs) {
                                for (String sbValue : sbValues) {
                                    appendedPotentialAPIURLs.add(potentialAPIURL + sbValue);
                                }
                            }
                        } else {
                            for (String sbValue : sbValues) {
                                appendedPotentialAPIURLs.add(sbValue);
                            }
                        }

                        potentialApiURLs = appendedPotentialAPIURLs;
                        continue;
                    }

                    parameterNbr++;
                }

                continue;
            }

            if (!methodCallExpr.getScope().isPresent()) {
                continue;
            }

            Expression scope = methodCallExpr.getScope().get();

            System.out.println("Checking: " + methodCallExpr + ", scope: " + scope);

            if (!(scope.isNameExpr() || scope.isFieldAccessExpr())) {
                while (scope.isMethodCallExpr()) {
                    if (!(((MethodCallExpr) scope).getScope()).isPresent()) {
                        break;
                    }
                    scope = ((MethodCallExpr) scope).getScope().get();
                }

                if (!(scope.isNameExpr() || scope.isFieldAccessExpr())) {
                    continue;
                }
            }

            ResolvedValueDeclaration resolvedValueDeclaration = null;
            if (scope.isNameExpr()) {
                resolvedValueDeclaration = scope.asNameExpr().resolve();
            } else if (scope.isFieldAccessExpr()) {
                resolvedValueDeclaration = scope.asFieldAccessExpr().resolve();
            }

            if (resolvedValueDeclaration == null) {
                continue;
            }

            if (((resolvedValueDeclaration instanceof JavaParserSymbolDeclaration)
                    && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                    .equals(variableOrFieldNode))
                    || ((resolvedValueDeclaration instanceof JavaParserFieldDeclaration)
                    && ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode()
                    .equals(variableOrFieldNode))
                    || (resolvedValueDeclaration instanceof JavaParserParameterDeclaration)
                    && ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode()
                    .equals(variableOrFieldNode)) {

                // Get value of append() MethodCallExpr argument
                if (methodCallExpr.getArguments().size() != 1) {
                    continue;
                }

                List<String> appendValues = getExpressionValue(methodCallExpr.getArguments().get(0));
                if (appendValues == null || appendValues.isEmpty()) {
                    continue;
                }

                System.out.println("Adding values: " + appendValues.size());

                if (methodCallExpr.getScope().get().isNameExpr() || methodCallExpr.getScope().get()
                        .isFieldAccessExpr()) {
                    List<String> appendValuesToCheck = new LinkedList<>();
                    for (String appendValue : appendValues) {
                        if (potentialApiURLs.isEmpty()) {
                            appendValuesToCheck.add(appendValue);
                        } else {
                            for (String preValue : potentialApiURLs) {
                                appendValuesToCheck.add(preValue + appendValue);
                            }
                        }
                    }

                    List<String> appendValuesToCheckStackIncl = new LinkedList<>();
                    while (!chainedValuesStack.isEmpty()) {
                        for (String appendValue : chainedValuesStack.pop()) {
                            for (String preValue : appendValuesToCheck) {
                                appendValuesToCheckStackIncl.add(preValue + appendValue);
                            }
                        }
                    }

                    if (!appendValuesToCheckStackIncl.isEmpty()) {
                        appendValuesToCheck = appendValuesToCheckStackIncl;
                    }

                    potentialApiURLs = appendValuesToCheck;
                } else {
                    chainedValuesStack.push(appendValues);
                }
            }
        }

        return potentialApiURLs;
    }

    public static List<String> serializeBinaryExpr(BinaryExpr binaryExpr) {
        List<String> toReturn = new ArrayList<>();

        if (!binaryExpr.getOperator().asString().equals("+")) {
            return null;
        }

        Expression leftExpression = binaryExpr.getLeft();
        Expression rightExpression = binaryExpr.getRight();

        if (leftExpression.isBinaryExpr()) {
            toReturn = serializeBinaryExpr(leftExpression.asBinaryExpr());
        } else if (leftExpression.isStringLiteralExpr()) {
            toReturn = Arrays.asList(leftExpression.asStringLiteralExpr().getValue());
        } else if (leftExpression.isNameExpr()) {
            toReturn = getExpressionValue(leftExpression.asNameExpr());
        } else {
            toReturn = getExpressionValue(leftExpression);
        }

        if (toReturn == null) {
            return null;
        }

        if (rightExpression.isBinaryExpr()) {
            List<String> appendedPaths = new ArrayList<>();
            for (String path : toReturn) {
                List<String> serializedBinaryExprPaths = serializeBinaryExpr(rightExpression.asBinaryExpr());
                if (serializedBinaryExprPaths == null) {
                    return null;
                }

                for (String toAppendPath : serializedBinaryExprPaths) {
                    appendedPaths.add(path + toAppendPath);
                }
            }

            toReturn = appendedPaths;
        } else if (rightExpression.isStringLiteralExpr()) {
            List<String> appendedPaths = new ArrayList<>();
            for (String path : toReturn) {
                appendedPaths.add(path + rightExpression.asStringLiteralExpr().getValue());
            }
            toReturn = appendedPaths;
        } else if (rightExpression.isNameExpr()) {
            List<String> appendedPaths = new ArrayList<>();
            for (String path: toReturn) {
                List<String> serializedNameExprPaths = getExpressionValue(rightExpression.asNameExpr());
                if (serializedNameExprPaths == null) {
                    return null;
                }

                for (String toAppendPath : serializedNameExprPaths) {
                    appendedPaths.add(path + toAppendPath);
                }
            }

            toReturn = appendedPaths;
        } else {
            List<String> appendPaths = new ArrayList<>();
            for (String path : toReturn) {
                List<String> toAdd = getExpressionValue(rightExpression);
                if (toAdd == null) {
                    return null;
                }

                for (String toAppendPath : toAdd) {
                    appendPaths.add(path + toAppendPath);
                }
            }

            toReturn = appendPaths;
        }

        return toReturn;
    }

    private static String solvedExpressionType(Expression expression) {
        String estimatedType = TypeEstimator.estimateTypeName(expression);

        System.out.println("Expression: " + expression.toString() + ", type: " + estimatedType);

        if (estimatedType == null) {
            return null;
        }

        switch (estimatedType) {
            case "java.lang.String":
                return "<STRING>";
            case "java.lang.Double": case "java.lang.Float":
                return "<DOUBLE";
            case "java.lang.Integer":
                return "<INTEGER>";
            case "java.lang.Boolean":
                return "<BOOLEAN>";
            default:
                return null;
        }
    }

    private static boolean contains(Node compareNode, Node tempNode) {
        if (compareNode.equals(tempNode)) {
            return true;
        }

        boolean contains = false;
        for (Node child : tempNode.getChildNodes()) {
            contains = contains(compareNode, child) || contains;
        }

        return contains;
    }

    public static List<String> getExpressionValue(Expression expression) {
        System.out.println("Getting value of expression: " + expression);

        if (expression.isNameExpr()) {
            System.out.println("NameExpr found: " + expression);
            List<Node> assignedNodes = AssignmentLocator.nameExprGetLastAssignedNode(expression.asNameExpr(), project);
            List<String> toReturn = new LinkedList<>();
            System.out.println("Assigned nodes to check: " + assignedNodes);

            if (assignedNodes == null) {
                String typeString = solvedExpressionType(expression.asNameExpr());
                if (typeString != null) {
                    return Arrays.asList(typeString);
                } else {
                    return null;
                }
            }

            for (Node assignedNode : assignedNodes) {
                if (assignedNode instanceof Expression) {
                    if (assignedNode.containsWithin((Node) expression)) {
                        System.out.println("Dont look at node itself");
                        continue;
                    }
                    System.out.println("Assigned expression: " + assignedNode);
                    List<String> toAdd = getExpressionValue((Expression) assignedNode);
                    if (toAdd != null) {
                        toReturn.addAll(toAdd);
                    }
                } else {
                    System.out.println("Not an expression: " + assignedNode);
                }
            }

            if (toReturn.isEmpty()) {
                String typeString = solvedExpressionType(expression.asNameExpr());
                if (typeString != null) {
                    return Arrays.asList(typeString);
                } else {
                    return null;
                }
            } else {
                return toReturn;
            }
        } else if (expression.isStringLiteralExpr()) {
            return Arrays.asList(expression.asStringLiteralExpr().getValue());
        } else if (expression.isIntegerLiteralExpr()) {
            return Arrays.asList(expression.asIntegerLiteralExpr().getValue());
        } else if (expression.isBooleanLiteralExpr()) {
            return Arrays.asList(Boolean.toString(expression.asBooleanLiteralExpr().getValue()));
        } else if (expression.isDoubleLiteralExpr()) {
            return Arrays.asList(expression.asDoubleLiteralExpr().getValue());
        } else if (expression.isLongLiteralExpr()) {
            return Arrays.asList(expression.asLongLiteralExpr().getValue());
        } else if (expression.isNullLiteralExpr()) {
            return Arrays.asList("null");
        } else if (expression.isBinaryExpr()) {
            return serializeBinaryExpr(expression.asBinaryExpr());
        } else if (expression.isMethodCallExpr()) {
            System.out.println("MethodCallExpr found: " + expression);

            if (expression.asMethodCallExpr().getName().asString().equals("concat")) {
                return extractStringConcatValue(expression.asMethodCallExpr());
            } else {
                ResolvedMethodDeclaration resolvedMethodDeclaration = null;
                try {
                    resolvedMethodDeclaration = expression.asMethodCallExpr().resolve();
                } catch (Exception e) {
                    System.out.println("Error resolving MethodCallExpr: " + e);
                }

                System.out.println("Resolved MethodCallExpr: " + resolvedMethodDeclaration);

                if (resolvedMethodDeclaration == null) {
                    return null;
                }

                if (resolvedMethodDeclaration instanceof ReflectionMethodDeclaration) {
                    System.out.println("Found ReflectionMethodDeclaration");

                    String typeString = solvedExpressionType(expression.asMethodCallExpr());
                    if (typeString != null) {
                        return Arrays.asList(typeString);
                    } else {
                        return null;
                    }
                }

                MethodDeclaration methodDeclaration = ((JavaParserMethodDeclaration) resolvedMethodDeclaration)
                        .getWrappedNode();

                methodDeclaration = DeclarationLocator.locate(methodDeclaration, MethodDeclaration.class);

                if (methodDeclaration == null) {
                    return null;
                }

                List<ReturnStmt> returnStmts = methodDeclaration.findAll(ReturnStmt.class);
                List<String> toReturn = new LinkedList<>();
                for (ReturnStmt returnStmt : returnStmts) {
                    System.out.println("Found return statement: " + returnStmt);

                    List<String> returnExprResult = getExpressionValue(returnStmt.getExpression().get());

                    if (returnExprResult == null) {
                        continue;
                    }

                    toReturn.addAll(returnExprResult);
                }

                if (toReturn == null || toReturn.isEmpty()) {
                    String typeString = solvedExpressionType(expression.asMethodCallExpr());
                    if (typeString != null) {
                        return Arrays.asList(typeString);
                    } else {
                        return null;
                    }
                }

                return toReturn;
            }
        } else if (expression.isObjectCreationExpr()) {
            if (expression.asObjectCreationExpr().getType().getName().asString().equals("URL")) {
                return extractURLValue(expression.asObjectCreationExpr());
            }
        } else if (expression.isFieldAccessExpr()) {
            System.out.println("Field expression found: " + expression);

            ResolvedValueDeclaration resolvedValueDeclaration;
            try {
                resolvedValueDeclaration = expression.asFieldAccessExpr().resolve();
            } catch (Exception e) {
                System.out.println("Error resolving field access expression: " + e);
                return null;
            }

            if (resolvedValueDeclaration instanceof JavaParserFieldDeclaration) {
                Node declarationNode = ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode();

                if (declarationNode instanceof FieldDeclaration) {
                    VariableDeclarator variableDeclarator = null;
                    for (VariableDeclarator vd : ((FieldDeclaration) declarationNode).getVariables()) {
                        if (vd.getName().asString().equals(expression.asFieldAccessExpr().getName().asString())) {
                            variableDeclarator = vd;
                        }
                    }

                    if (variableDeclarator == null) {
                        String typeString = solvedExpressionType(expression.asFieldAccessExpr());
                        if (typeString != null) {
                            return Arrays.asList(typeString);
                        } else {
                            return null;
                        }
                    }

                    if (variableDeclarator.getInitializer().isPresent()) {
                        return getExpressionValue(variableDeclarator.getInitializer().get());
                    }
                }
            } else if (resolvedValueDeclaration instanceof ReflectionFieldDeclaration) {
                System.out.println("ReflectionFieldDeclaration found: " + resolvedValueDeclaration);
                if (resolvedValueDeclaration.getName().equals("NULL")) {
                    return Arrays.asList("NULL");
                }
            } else {
                System.out.println("Not a JavaParserFieldDeclaration: " + resolvedValueDeclaration);
            }

            String typeString = solvedExpressionType(expression.asFieldAccessExpr());
            if (typeString != null) {
                return Arrays.asList(typeString);
            } else {
                return null;
            }
        } else {
            System.out.println("NOT IMPLEMENTED: " + expression);

            String typeString = solvedExpressionType(expression);
            if (typeString != null) {
                return Arrays.asList(typeString);
            } else {
                return null;
            }
        }

        return null;
    }

    /*
    public static List<String> getExpressionValue2(Expression expression) {
        System.out.println("Getting value of expression: " + expression);

        if (expression.isNameExpr()) {
            ResolvedValueDeclaration resolvedValueDeclaration;
            try {
                resolvedValueDeclaration = expression.asNameExpr().resolve();
            } catch (Exception e) {
                System.out.println("Error resolving NameExpr: " + e);
                return null;
            }

            System.out.println("ResolvedValueDec: " + resolvedValueDeclaration);

            if (resolvedValueDeclaration.isVariable()) {
                // TODO: Check if I even need this...
                System.out.println("Found a variable");
            } else if (resolvedValueDeclaration.isField()) {
                Node declarationNode = ((JavaParserFieldDeclaration) resolvedValueDeclaration.asField())
                        .getWrappedNode();

                if (((FieldDeclaration) declarationNode).asFieldDeclaration().getVariables().size() == 1) {
                    VariableDeclarator variableDeclarator = ((FieldDeclaration) declarationNode)
                            .asFieldDeclaration().getVariables().get(0);
                    if (variableDeclarator.getInitializer().isPresent()) {
                        if (variableDeclarator.getInitializer().get().isStringLiteralExpr()) {
                            return Arrays.asList(variableDeclarator.getInitializer().get().asStringLiteralExpr().getValue());
                        } else {
                            return getExpressionValue2(variableDeclarator.getInitializer().get());
                        }
                    }
                }
            } else if (resolvedValueDeclaration.isParameter()) {
                Node declarationNode = (((JavaParserParameterDeclaration) resolvedValueDeclaration.asParameter())
                        .getWrappedNode());
                System.out.println("Declaration node: " + declarationNode + ", parameter type: "
                        + resolvedValueDeclaration.asParameter().getType());

                if (!((JavaParserParameterDeclaration) resolvedValueDeclaration.asParameter()).getWrappedNode().getParentNode().isPresent()) {
                    return null;
                }

                Node parentNode = ((JavaParserParameterDeclaration) resolvedValueDeclaration.asParameter()).getWrappedNode().getParentNode().get();
                System.out.println("Parent node: " + parentNode);

                if (!(parentNode instanceof MethodDeclaration)) {
                    return null;
                }

                MethodDeclaration methodDeclaration = (MethodDeclaration) parentNode;

                if (!methodDeclaration.findCompilationUnit().isPresent()) {
                    return null;
                }

                List<MethodCallExpr> methodCallExprs = new LinkedList<>();

                if (methodDeclaration.isPublic()) {
                    for (CompilationUnit cu : project.compilationUnits) {
                        methodCallExprs.addAll(cu.findAll(MethodCallExpr.class));
                    }
                } else {
                    methodCallExprs = methodDeclaration.findCompilationUnit().get().findAll(MethodCallExpr.class);
                }

                List<String> toReturn = new ArrayList<>();
                for (MethodCallExpr methodCallExpr : methodCallExprs) {
                    if (methodCallExpr.getName().asString().equals(methodDeclaration.getNameAsString())) {
                        System.out.println("Found matching method call in compilation unit: " + methodCallExpr);

                        String methodCallExprQSignature;
                        String methodDeclarationQSignature;
                        int parameterPosition = 0;
                        try {
                            methodCallExprQSignature = methodCallExpr.resolve().getQualifiedSignature();
                            ResolvedMethodDeclaration resolvedMethodDeclaration = methodDeclaration.resolve();
                            methodDeclarationQSignature = resolvedMethodDeclaration.getQualifiedSignature();

                            for (int i = 0; i < resolvedMethodDeclaration.getNumberOfParams(); i++) {
                                if (resolvedMethodDeclaration.getParam(i).hasName() && resolvedMethodDeclaration
                                        .getParam(i).getName().equals(resolvedValueDeclaration.getName())) {
                                    parameterPosition = i;
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Signatures could not be determined: " + e);
                            return null;
                        }

                        if (methodCallExprQSignature.equals(methodDeclarationQSignature)) {
                            System.out.println("Signatures match: " + methodCallExprQSignature
                                    + "parameter position: " + parameterPosition);
                            List<String> expressionValues = getExpressionValue2(methodCallExpr.getArgument(parameterPosition));
                            if (expressionValues != null) {
                                toReturn.addAll(expressionValues);
                            }
                        } else {
                            System.out.println("Signatures don't match: " + methodCallExpr.resolve().getQualifiedSignature() + ", and: " + methodDeclaration.resolve().getQualifiedSignature());
                        }
                    }
                }

                if (toReturn.size() == 0) {
                    System.out.println("Method " + methodDeclaration.getNameAsString() +
                            " is either not used or called from another class.");
                    // TODO: Deal with this...
                    return null;
                }

                return toReturn;
            } else if (resolvedValueDeclaration instanceof JavaParserSymbolDeclaration) {
                System.out.println("Found JavaParserSymbolDeclaration");
                Node declarationNode = ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode();
                if (declarationNode instanceof VariableDeclarator) {
                    if (((VariableDeclarator) declarationNode).getInitializer().isPresent()) {
                        return getExpressionValue2(((VariableDeclarator) declarationNode).getInitializer().get());
                    }
                }
            }
        } else if (expression.isStringLiteralExpr()) {
            return Arrays.asList(expression.asStringLiteralExpr().getValue());
        } else if (expression.isIntegerLiteralExpr()) {
            return Arrays.asList(expression.asIntegerLiteralExpr().getValue());
        } else if (expression.isBooleanLiteralExpr()) {
            return Arrays.asList(Boolean.toString(expression.asBooleanLiteralExpr().getValue()));
        } else if (expression.isDoubleLiteralExpr()) {
            return Arrays.asList(expression.asDoubleLiteralExpr().getValue());
        } else if (expression.isLongLiteralExpr()) {
            return Arrays.asList(expression.asLongLiteralExpr().getValue());
        } else if (expression.isNullLiteralExpr()) {
            return Arrays.asList("null");
        } else if (expression.isBinaryExpr()) {
            return serializeBinaryExpr(expression.asBinaryExpr());
        } else if (expression.isMethodCallExpr()) {
            if (expression.asMethodCallExpr().getName().asString().equals("concat")) {
                return extractStringConcatValue(expression.asMethodCallExpr());
            } else {
                ResolvedMethodDeclaration resolvedMethodDeclaration = null;
                try {
                    resolvedMethodDeclaration = expression.asMethodCallExpr().resolve();
                } catch (Exception e) {
                    System.out.println("Error resolving MethodCallExpr: " + e);
                }

                System.out.println("Resolved MethodCallExpr: " + resolvedMethodDeclaration);

                if (resolvedMethodDeclaration == null) {
                    return null;
                }

                if (resolvedMethodDeclaration instanceof ReflectionMethodDeclaration) {
                    // TODO: Return type as string: <TYPE INFO>
                    return null;
                }

                MethodDeclaration methodDeclaration = ((JavaParserMethodDeclaration) resolvedMethodDeclaration)
                        .getWrappedNode();

                methodDeclaration = DeclarationLocator.locate(methodDeclaration, MethodDeclaration.class);

                List<ReturnStmt> returnStmts = methodDeclaration.findAll(ReturnStmt.class);
                List<String> toReturn = new LinkedList<>();
                for (ReturnStmt returnStmt : returnStmts) {
                    System.out.println("Found return statement: " + returnStmt);

                    List<String> returnExprResult = getExpressionValue2(returnStmt.getExpression().get());

                    if (returnExprResult == null) {
                        continue;
                    }

                    toReturn.addAll(returnExprResult);
                }

                if (toReturn == null || toReturn.isEmpty()) {
                    return null;
                }

                return toReturn;
            }
        } else if (expression.isObjectCreationExpr()) {
            if (expression.asObjectCreationExpr().getType().getName().asString().equals("URL")) {
                return extractURLValue(expression.asObjectCreationExpr());
            }
        } else if (expression.isFieldAccessExpr()) {
            System.out.println("Field expression found: " + expression);

            ResolvedValueDeclaration resolvedValueDeclaration;
            try {
                resolvedValueDeclaration = expression.asFieldAccessExpr().resolve();
            } catch (Exception e) {
                System.out.println("Error resolving field access expression: " + e);
                return null;
            }

            if (resolvedValueDeclaration instanceof JavaParserFieldDeclaration) {
                Node declarationNode = ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode();

                if (declarationNode instanceof FieldDeclaration) {
                    VariableDeclarator variableDeclarator = null;
                    for (VariableDeclarator vd : ((FieldDeclaration) declarationNode).getVariables()) {
                        if (vd.getName().asString().equals(expression.asFieldAccessExpr().getName().asString())) {
                            variableDeclarator = vd;
                        }
                    }

                    if (variableDeclarator == null) {
                        return null;
                    }

                    if (variableDeclarator.getInitializer().isPresent()) {
                        return getExpressionValue2(variableDeclarator.getInitializer().get());
                    }
                }
            } else {
                System.out.println("Not a JavaParserFieldDeclaration: " + resolvedValueDeclaration);
            }
        } else {
            System.out.println("NOT IMPLEMENTED: " + expression);
        }

        return null;
    }
    */

}
