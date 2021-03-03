//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 11.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package ch.unibe.scg.jandrolyzer.EndpointExtraction;

import ch.unibe.scg.jandrolyzer.Models.Project;
import ch.unibe.scg.jandrolyzer.*;
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

import java.util.*;

public class ExpressionValueExtraction {

    static Project project;

    /*
     * Extract API URLs from java.net.URL ObjectCreationExpr
     *
     * Returns list of value Strings or null if an error occurred
     */
    public static List<String> extractURLValue(ObjectCreationExpr objectCreationExpr, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return null;
        }
        depthLevel++;

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
            toReturn = getExpressionValue(objectCreationExpr.getArgument(0), null, depthLevel);
        } else if (objectCreationExpr.getArguments().size() == 2) {
            List<String> context = getExpressionValue(objectCreationExpr.getArgument(0), null, depthLevel);
            List<String> spec = getExpressionValue(objectCreationExpr.getArgument(1), null, depthLevel);

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
            List<String> protocols = getExpressionValue(objectCreationExpr.getArgument(0), null, depthLevel);
            List<String> host = getExpressionValue(objectCreationExpr.getArgument(1), null, depthLevel);
            List<String> file = getExpressionValue(objectCreationExpr.getArgument(2), null, depthLevel);

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
            List<String> protocols = getExpressionValue(objectCreationExpr.getArgument(0), null, depthLevel);
            List<String> host = getExpressionValue(objectCreationExpr.getArgument(1), null, depthLevel);
            List<String> port = getExpressionValue(objectCreationExpr.getArgument(2), null, depthLevel);
            List<String> file = getExpressionValue(objectCreationExpr.getArgument(3), null, depthLevel);

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

        if (toReturn == null || toReturn.isEmpty()) {
            return null;
        } else {
            return toReturn;
        }
    }

    public static List<String> extractStringConcatValue(MethodCallExpr methodCallExpr, Set<Node> seenExpressions, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return null;
        }
        depthLevel++;

        if (methodCallExpr.getArguments().size() != 1 || !methodCallExpr.getScope().isPresent()) {
            return null;
        }

        Expression scope = methodCallExpr.getScope().get();
        List<String> toCheck = getExpressionValue(methodCallExpr.getArgument(0), seenExpressions, depthLevel);

        if (toCheck == null || !(scope.isMethodCallExpr() || scope.isNameExpr() || scope.isFieldAccessExpr())) {
            return null;
        }

        List<String> prePath = null;
        if (scope.isMethodCallExpr()) {
            prePath = extractStringConcatValue((MethodCallExpr) scope, seenExpressions, depthLevel);
        } else if (scope.isNameExpr() || scope.isFieldAccessExpr()) {
            prePath = getExpressionValue(scope, seenExpressions, depthLevel);
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

    public static List<String> extractStringBuilderValue(VariableDeclarator variableDeclarator, Project project, int depthLevel) {
        Node containingNode = Utils.getParentClassOrMethod(variableDeclarator);

        List<String> potentialApiURLs = new LinkedList<>();
        if (containingNode instanceof MethodDeclaration) {
            List<MethodCallExpr> methodCallExprs = containingNode.findAll(MethodCallExpr.class);
            System.out.println("Extracting StringBuilder values in MethodDeclaration");
            potentialApiURLs = reconstructStringBuilderStringsIn(methodCallExprs, variableDeclarator, depthLevel);
            System.out.println("Finished extracting StringBuilder values");
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
                System.out.println("Extracting StringBuilder values in MethodDeclarations: " + methodDeclarations.size());
                for (MethodDeclaration methodDeclaration : methodDeclarations) {
                    List<MethodCallExpr> methodCallExprs = methodDeclaration.findAll(MethodCallExpr.class);
                    potentialApiURLs.addAll(reconstructStringBuilderStringsIn(methodCallExprs, fieldNode, depthLevel));
                }
                System.out.println("Finished extracting StringBuilder values in MethodDeclarations");
            }
        }

        if (!potentialApiURLs.isEmpty()) {
            return potentialApiURLs;
        }

        return null;
    }

    public static List<String> reconstructStringBuilderStringsIn(List<MethodCallExpr> methodCallExprs,
                                                           Node variableOrFieldNode, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return null;
        }
        depthLevel++;

        List<String> potentialApiURLs = new LinkedList<>();
        Stack<List<String>> chainedValuesStack = new Stack<>();
        for (MethodCallExpr methodCallExpr : methodCallExprs) {
            // Comment the next 3 lines if execution time is not an issue
            if (potentialApiURLs.size() > 100) {
                return potentialApiURLs;
            }

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

                    ResolvedType resolvedType = null;
                    try {
                        resolvedType = resolvedValueDeclaration.getType();
                    } catch (Exception e) {
                        continue;
                    }

                    if (!(resolvedType.isReferenceType() && resolvedType.asReferenceType().getQualifiedName()
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
                        System.out.println("Reconstructing StringBuilder string from methodCallExpr");
                        List<String> sbValues = reconstructStringBuilderStringsIn(mMethodCallExpr, methodDeclaration.getParameter(parameterNbr), depthLevel);
                        System.out.println("Reconstruct StringBuilder string from methodCallExpr finished, size: " + sbValues.size());

                        if (sbValues.isEmpty()) {
                            continue;
                        }

                        System.out.println("PotentialAPIURLs size: " + potentialApiURLs.size());

                        List<String> appendedPotentialAPIURLs = new ArrayList<>();
                        if (!potentialApiURLs.isEmpty()) {
                            for (String potentialAPIURL : potentialApiURLs) {
                                for (String sbValue : sbValues) {
                                    appendedPotentialAPIURLs.add(potentialAPIURL + sbValue);
                                }
                            }
                        } else {
                            for (String sbValue : sbValues) {
                                System.out.println("appendedPotentialAPIURLs size: " + appendedPotentialAPIURLs.size());
                                appendedPotentialAPIURLs.add(sbValue);
                            }
                        }

                        potentialApiURLs = appendedPotentialAPIURLs;

                        System.out.println("Added to potentialApiURLs");

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
                try {
                    resolvedValueDeclaration = scope.asNameExpr().resolve();
                } catch (Exception e) {
                    System.out.println("Error resolving NameExpr: " + e);
                }
            } else if (scope.isFieldAccessExpr()) {
                try {
                    resolvedValueDeclaration = scope.asFieldAccessExpr().resolve();
                } catch (Exception e) {
                    System.out.println("Error resolving NameExpr: " + e);
                }
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

                List<String> appendValues = getExpressionValue(methodCallExpr.getArguments().get(0), null, depthLevel);
                if (appendValues == null || appendValues.isEmpty()) {
                    continue;
                }

                System.out.println("Adding values: " + appendValues.size());

                if (methodCallExpr.getScope().get().isNameExpr() || methodCallExpr.getScope().get()
                        .isFieldAccessExpr()) {
                    List<String> appendValuesToCheck = new LinkedList<>();
                    System.out.println("appendValues size: " + appendValues.size());
                    System.out.println("potentialApiURLs size: " + potentialApiURLs.size());
                    for (String appendValue : appendValues) {
                        if (potentialApiURLs.isEmpty()) {
                            appendValuesToCheck.add(appendValue);
                        } else {
                            for (String preValue : potentialApiURLs) {
                                appendValuesToCheck.add(preValue + appendValue);
                            }
                        }
                    }

                    System.out.println("Stack size: " + chainedValuesStack.size());

                    List<String> appendValuesToCheckStackIncl = new LinkedList<>();
                    while (!chainedValuesStack.isEmpty()) {
                        for (String appendValue : chainedValuesStack.pop()) {
                            System.out.println("Adding from stack: " + appendValue);
                            for (String preValue : appendValuesToCheck) {
                                appendValuesToCheckStackIncl.add(preValue + appendValue);
                            }
                        }
                    }

                    if (!appendValuesToCheckStackIncl.isEmpty()) {
                        appendValuesToCheck = appendValuesToCheckStackIncl;
                    }

                    System.out.println("appendValuesToCheck size: " + appendValuesToCheck.size());

                    potentialApiURLs = appendValuesToCheck;

                    System.out.println("Added");
                } else {
                    System.out.println("Pushing to stack, stacksize: " + chainedValuesStack.size());
                    chainedValuesStack.push(appendValues);
                    System.out.println("Pushed to stack");
                }
            }
        }

        return potentialApiURLs;
    }

    public static List<String> serializeBinaryExpr(BinaryExpr binaryExpr, Set<Node> seenExpressions, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return null;
        }
        depthLevel++;

        List<String> toReturn = new ArrayList<>();

        if (!binaryExpr.getOperator().asString().equals("+")) {
            System.out.println("Not a plus operation, returning...");
            return null;
        }

        Expression leftExpression = binaryExpr.getLeft();
        Expression rightExpression = binaryExpr.getRight();

        if (leftExpression.isBinaryExpr()) {
            toReturn = serializeBinaryExpr(leftExpression.asBinaryExpr(), seenExpressions, depthLevel);
        } else if (leftExpression.isStringLiteralExpr()) {
            toReturn = Arrays.asList(leftExpression.asStringLiteralExpr().getValue());
        } else if (leftExpression.isNameExpr()) {
            toReturn = getExpressionValue(leftExpression.asNameExpr(), seenExpressions, depthLevel);
        } else {
            toReturn = getExpressionValue(leftExpression, seenExpressions, depthLevel);
        }

        if (toReturn == null) {
            return null;
        }

        if (rightExpression.isBinaryExpr()) {
            List<String> appendedPaths = new ArrayList<>();
            for (String path : toReturn) {
                List<String> serializedBinaryExprPaths = serializeBinaryExpr(rightExpression.asBinaryExpr(), seenExpressions, depthLevel);
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
                List<String> serializedNameExprPaths = getExpressionValue(rightExpression.asNameExpr(), seenExpressions, depthLevel);
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
                List<String> toAdd = getExpressionValue(rightExpression, seenExpressions, depthLevel);
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
            if (contains) {
                break;
            }
        }

        return contains;
    }

    public static List<String> getExpressionValue(Expression expression, Set<Node> seenExpressions, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");

            String typeString = solvedExpressionType(expression);
            if (typeString != null) {
                return Arrays.asList(typeString);
            } else {
                return null;
            }
        }
        depthLevel++;

        System.out.println("Getting value of expression: " + expression);

        if (expression.isNameExpr()) {
            System.out.println("NameExpr found: " + expression);
            List<Node> assignedNodes = AssignmentLocator.nameExprGetLastAssignedNode(expression.asNameExpr(), project, 0);
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

            if (seenExpressions != null) {
                seenExpressions.add(expression);
            } else {
                seenExpressions = new HashSet<>();
                seenExpressions.add(expression);
            }

            System.out.println("Seen expressions size: " + seenExpressions.size());
            System.out.println("Seen expressions: " + seenExpressions);

            for (Node assignedNode : assignedNodes) {
                if (assignedNode instanceof Expression) {
                    /*
                    if (assignedNode.containsWithin((Node) expression)) {
                        System.out.println("Dont look at node itself");
                        continue;
                    } */
                    boolean shouldSkip = false;
                    for (Node e : seenExpressions) {
                        /*
                        if (assignedNode.containsWithin(e)) {
                            shouldSkip = true;
                            break;
                        }*/
                        if (contains(e, assignedNode)) {
                            shouldSkip = true;
                            break;
                        }
                    }
                    if (shouldSkip) {
                        System.out.println("Skipping already seen expression");
                        continue;
                    }
                    System.out.println("Assigned expression: " + assignedNode);
                    List<String> toAdd = getExpressionValue((Expression) assignedNode, seenExpressions, depthLevel);
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
            return serializeBinaryExpr(expression.asBinaryExpr(), seenExpressions, depthLevel);
        } else if (expression.isMethodCallExpr()) {
            System.out.println("MethodCallExpr found: " + expression);

            if (expression.asMethodCallExpr().getName().asString().equals("concat")) {
                return extractStringConcatValue(expression.asMethodCallExpr(), seenExpressions, depthLevel);
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

                if (seenExpressions != null && seenExpressions.contains(methodDeclaration)) {
                    System.out.println("Already visited methodDeclaration");
                    return null;
                }

                if (seenExpressions == null) {
                    seenExpressions = new HashSet<>();
                    seenExpressions.add(methodDeclaration);
                } else {
                    seenExpressions.add(methodDeclaration);
                }

                List<ReturnStmt> returnStmts = methodDeclaration.findAll(ReturnStmt.class);
                List<String> toReturn = new LinkedList<>();
                for (ReturnStmt returnStmt : returnStmts) {
                    System.out.println("Found return statement: " + returnStmt);

                    if (!returnStmt.getExpression().isPresent()) {
                        continue;
                    }

                    boolean shouldSkip = false;
                    if (seenExpressions != null) {
                        for (Node e : seenExpressions) {
                            if (contains(e, returnStmt.getExpression().get())) {
                                shouldSkip = true;
                                break;
                            }
                        }
                    }

                    if (shouldSkip) {
                        System.out.println("Skipping return stmt: " + returnStmt);
                        continue;
                    }

                    List<String> returnExprResult = getExpressionValue(returnStmt.getExpression().get(), seenExpressions, depthLevel);

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
                return extractURLValue(expression.asObjectCreationExpr(), depthLevel);
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
                        return getExpressionValue(variableDeclarator.getInitializer().get(), seenExpressions, depthLevel);
                    }
                }
            } else if (resolvedValueDeclaration instanceof ReflectionFieldDeclaration) {
                System.out.println("ReflectionFieldDeclaration found: " + resolvedValueDeclaration);
                if (resolvedValueDeclaration.getName().equals("NULL")) {
                    return Arrays.asList("NULL");
                } else if (resolvedValueDeclaration.asField().getType().isReferenceType() &&
                        resolvedValueDeclaration.asField().getType().asReferenceType().getQualifiedName()
                                .equals("java.lang.Boolean")) {
                    System.out.println("Reflect: " + resolvedValueDeclaration.getName());
                    if (resolvedValueDeclaration.getName().equals("TRUE")) {
                        return Arrays.asList("TRUE");
                    } else {
                        return Arrays.asList("FALSE");
                    }
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
}
