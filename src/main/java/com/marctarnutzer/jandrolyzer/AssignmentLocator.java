//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 19.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.marctarnutzer.jandrolyzer.Models.Project;

import java.util.*;

public class AssignmentLocator {

    /*
     * Returns a list of possible nodes assigned to a nameExpr between its declaration and the nameExpr access
     * location in question
     */
    public static List<Node> nameExprGetLastAssignedNode(NameExpr nameExpr, Project project, int depthLevel) {
        System.out.println("ASSIGNMENTLOCATOR: Get last assigned node for: " + nameExpr);

        ResolvedValueDeclaration resolvedValueDeclaration;
        try {
            resolvedValueDeclaration = nameExpr.resolve();
        } catch (Exception e) {
            System.out.println("ASSIGNMENTLOCATOR error: Resolving name expr: " + e);
            return null;
        }

        if (resolvedValueDeclaration == null) {
            return null;
        }

        Node declarationNode = null;
        List<Node> lastAssignedNodes = null;
        if (resolvedValueDeclaration instanceof JavaParserSymbolDeclaration) {
            declarationNode = ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode();
            lastAssignedNodes = getSymbolDeclarationAssignmentNodes(nameExpr, declarationNode);
        } else if (resolvedValueDeclaration instanceof JavaParserParameterDeclaration) {
            declarationNode = ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode();
            lastAssignedNodes = getParameterDeclarationAssignmentNodes(declarationNode, project, depthLevel);
        } else if (resolvedValueDeclaration instanceof JavaParserFieldDeclaration) {
            declarationNode = ((JavaParserFieldDeclaration) resolvedValueDeclaration).getWrappedNode();
            lastAssignedNodes = getFieldDeclarationAssignmentNodes(nameExpr, declarationNode, project);
        } else {
            System.out.println("Not implemented");
        }

        if (lastAssignedNodes == null || lastAssignedNodes.isEmpty()) {
            return null;
        }

        return lastAssignedNodes;
    }

    private static List<Node> getFieldDeclarationAssignmentNodes(NameExpr nameExpr, Node declarationNode, Project project) {
        System.out.println("ASSIGNMENTLOCATOR: field dec node: " + declarationNode);

        if (!(declarationNode instanceof FieldDeclaration)) {
            System.out.println("Not a field");
            return null;
        }

        Node classParentNode = Utils.getParentClassOrMethod(declarationNode);
        Node accessMethodParentNode = Utils.getParentClassOrMethod(nameExpr);

        if (!(classParentNode instanceof ClassOrInterfaceDeclaration)
                || !(accessMethodParentNode instanceof MethodDeclaration)) {
            return null;
        }

        FieldDeclaration fieldDeclaration = ((FieldDeclaration) declarationNode);

        List<Node> lastAssignedNodes = new LinkedList<>();

        VariableDeclarator variableDeclarator = null;
        for (VariableDeclarator vd : fieldDeclaration.getVariables()) {
            if (vd.getName().asString().equals(nameExpr.getNameAsString())) {
                variableDeclarator = vd;
            }
        }

        if (variableDeclarator == null) {
            System.out.println("Unable to find variableDeclarator: " + declarationNode);
            return null;
        }

        if (variableDeclarator.getInitializer().isPresent()
                && !variableDeclarator.getInitializer().get().isNullLiteralExpr()) {
            lastAssignedNodes.add(variableDeclarator.getInitializer().get());
        }

        if (fieldDeclaration.isPublic()) {
            for (CompilationUnit cu : project.compilationUnits) {
                List<MethodDeclaration> methodDeclarations = cu.findAll(MethodDeclaration.class);
                for (MethodDeclaration methodDeclaration : methodDeclarations) {
                    getAssignExprsInMethod(nameExpr, declarationNode, lastAssignedNodes, 0, methodDeclaration, methodDeclaration);
                }
            }

            System.out.println("Detected nodes for public field: " + lastAssignedNodes);
        }
        /*
        else if (fieldDeclaration.isProtected()) {

        }
        */
        else if (!fieldDeclaration.isPrivate()) {
            if (declarationNode.findCompilationUnit().isPresent()) {
                if (declarationNode.findCompilationUnit().get().getPackageDeclaration().isPresent()) {
                    for (CompilationUnit cu : getCompilationUnitsInPackage(declarationNode.findCompilationUnit().get()
                            .getPackageDeclaration().get(), project)) {
                        List<MethodDeclaration> methodDeclarations = cu.findAll(MethodDeclaration.class);
                        for (MethodDeclaration methodDeclaration : methodDeclarations) {
                            getAssignExprsInMethod(nameExpr, declarationNode, lastAssignedNodes, 0,
                                    methodDeclaration, methodDeclaration);
                        }
                    }
                } else {
                    List<MethodDeclaration> methodDeclarations = declarationNode.findCompilationUnit().get().findAll(MethodDeclaration.class);
                    for (MethodDeclaration methodDeclaration : methodDeclarations) {
                        getAssignExprsInMethod(nameExpr, declarationNode, lastAssignedNodes, 0, methodDeclaration, methodDeclaration);
                    }
                }
            }

            System.out.println("Detected nodes for non private field: " + lastAssignedNodes);
        } else {
            if (declarationNode.findCompilationUnit().isPresent()) {
                List<MethodDeclaration> methodDeclarations = declarationNode.findCompilationUnit().get().findAll(MethodDeclaration.class);
                for (MethodDeclaration methodDeclaration : methodDeclarations) {
                    getAssignExprsInMethod(nameExpr, declarationNode, lastAssignedNodes, 0, methodDeclaration, methodDeclaration);
                }
            }

            System.out.println("Detected nodes for private field: " + lastAssignedNodes);
        }

        if (lastAssignedNodes.isEmpty()) {
            return null;
        }

        return lastAssignedNodes;
    }

    private static List<CompilationUnit> getCompilationUnitsInPackage(PackageDeclaration packageDeclaration, Project project) {
        List<CompilationUnit> compilationUnits = new LinkedList<>();

        for (CompilationUnit cu : project.compilationUnits) {
            if (cu.getPackageDeclaration().isPresent() && cu.getPackageDeclaration().get().equals(packageDeclaration)) {
                compilationUnits.add(cu);
            }
        }

        return compilationUnits;
    }

    private List<CompilationUnit> getCompilationUnitsInPackageOrFromSubclasses(PackageDeclaration packageDeclaration,
                                                                               ClassOrInterfaceDeclaration classOrInterfaceDeclaration, Project project) {
        List<CompilationUnit> compilationUnits = new LinkedList<>();

        for (CompilationUnit cu : project.compilationUnits) {
            for (TypeDeclaration typeDeclaration : cu.getTypes()) {
                if (!typeDeclaration.isClassOrInterfaceDeclaration()) {
                    continue;
                }



                for (ClassOrInterfaceType classOrInterfaceType : ((ClassOrInterfaceDeclaration) typeDeclaration).getExtendedTypes()) {
                    ResolvedReferenceType resolvedReferenceType = classOrInterfaceType.resolve();

                }
            }

            if (cu.getPackageDeclaration().isPresent() && cu.getPackageDeclaration().equals(packageDeclaration)) {
                compilationUnits.add(cu);
            }
        }

        return compilationUnits;
    }

    private static List<Node> getParameterDeclarationAssignmentNodes(Node declarationNode, Project project, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return null;
        }
        depthLevel++;

        System.out.println("ASSIGNMENTLOCATOR: Parameter dec node: " + declarationNode);

        if (!(declarationNode instanceof Parameter)) {
            System.out.println("Not a parameter");
            return null;
        }

        Node parentNode = Utils.getParentClassOrMethod(declarationNode);

        if (!(parentNode instanceof MethodDeclaration)) {
            return null;
        }

        System.out.println("Method Dec: " + parentNode);

        MethodDeclaration methodDeclaration = (MethodDeclaration) parentNode;
        ResolvedMethodDeclaration resolvedMethodDeclaration = null;
        try {
            resolvedMethodDeclaration = methodDeclaration.resolve();
        } catch (Exception e) {
            System.out.println("ASSIGNMENTLOCATOR error resolving MethodDec: " + e);
            return null;
        }

        if (resolvedMethodDeclaration == null) {
            return null;
        }

        String methodDeclarationQSignature = null;
        try {
            methodDeclarationQSignature = resolvedMethodDeclaration.getQualifiedSignature();
        } catch (Exception e) {
            System.out.println("Error getting qualified signature: " + e);
            return null;
        }

        if (methodDeclaration == null) {
            return null;
        }

        int parameterPosition = -1;
        for (Parameter param : methodDeclaration.getParameters()) {
            parameterPosition++;
            if (param.equals((Parameter) declarationNode)) {
                break;
            }
        }

        if (parameterPosition == -1) {
            return null;
        }

        List<MethodCallExpr> methodCallExprs = new LinkedList<>();
        if (methodDeclaration.isPublic()) {
            for (CompilationUnit cu : project.compilationUnits) {
                methodCallExprs.addAll(cu.findAll(MethodCallExpr.class));
            }
        } else {
            if (!methodDeclaration.findCompilationUnit().isPresent()) {
                return null;
            }

            methodCallExprs.addAll(methodDeclaration.findCompilationUnit().get().findAll(MethodCallExpr.class));
        }

        System.out.println("Number of methodCallExprs found: " + methodCallExprs.size());

        /*
        if (methodCallExprs.size() > 100) {
            return null;
        }
        */

        List<Node> lastAssignedNodes = new LinkedList<>();
        for (MethodCallExpr methodCallExpr : methodCallExprs) {
            if (!methodCallExpr.getNameAsString().equals(methodDeclaration.getNameAsString())
                    || methodCallExpr.getArguments().size() < parameterPosition) {
                continue;
            }

            System.out.println("Found matching method call: " + methodCallExpr);

            String methodCallQSignature = null;
            try {
                methodCallQSignature = methodCallExpr.resolve().getQualifiedSignature();
            } catch (Exception e) {
                System.out.println("ASSIGNMENTLOCATOR error resolving methodCallExpr: " + e);
                continue;
            }

            if (methodCallQSignature.equals(methodDeclarationQSignature)) {
                System.out.println("Found match: " + methodCallExpr);
                Expression argExpr = methodCallExpr.getArgument(parameterPosition);
                if (argExpr.isNameExpr()) {
                    System.out.println("Its a nameExpr: " + argExpr);

                    // Check if nameExpr resolves not to the same Parameter
                    ResolvedValueDeclaration resolvedValueDeclaration;
                    try {
                        resolvedValueDeclaration = argExpr.asNameExpr().resolve();
                    } catch (Exception e) {
                        System.out.println("ASSIGNMENTLOCATOR error: Resolving name expr: " + e);
                        return null;
                    }

                    if (resolvedValueDeclaration == null) {
                        return null;
                    }

                    if (resolvedValueDeclaration instanceof JavaParserParameterDeclaration) {
                        Node declarationNode2 = ((JavaParserParameterDeclaration) resolvedValueDeclaration).getWrappedNode();
                        Node parentNode2 = Utils.getParentClassOrMethod(declarationNode2);

                        if (parentNode2.equals(parentNode)) {
                            System.out.println("Skip this argExpr...");
                            return null;
                        }
                    }

                    List<Node> lastAssigned = nameExprGetLastAssignedNode(argExpr.asNameExpr(), project, depthLevel);
                    if (lastAssigned != null) {
                        lastAssignedNodes.addAll(lastAssigned);
                    }
                } else if (argExpr.isLiteralExpr()) {
                    System.out.println("Its a LiteralExpr: " + argExpr);
                    lastAssignedNodes.add(argExpr.asLiteralExpr());
                }
            }
        }

        System.out.println("Collected lastAssignedNodes: " + lastAssignedNodes);

        if (lastAssignedNodes == null || lastAssignedNodes.isEmpty()) {
            return null;
        } else {
            return lastAssignedNodes;
        }
    }

    private static List<Node> getSymbolDeclarationAssignmentNodes(NameExpr nameExpr, Node declarationNode) {
        System.out.println("ASSIGNMENTLOCATOR: symbol dec node: " + declarationNode);
        if (!nameExpr.getBegin().isPresent()) {
            return null;
        }

        int nameExprPos = nameExpr.getBegin().get().line;

        if (!(declarationNode instanceof VariableDeclarator)) {
            System.out.println("Not a variable declarator");
            return null;
        }

        Node nameExprParentMethod = Utils.getParentClassOrMethod(nameExpr);
        Node declarationNodeParentMethod = Utils.getParentClassOrMethod(declarationNode);

        if (!(nameExprParentMethod.equals(declarationNodeParentMethod) && nameExprParentMethod instanceof MethodDeclaration)) {
            System.out.println("Invalid nameExpr or declarationNode");
            return null;
        }

        List<Node> lastAssignedNodes = new LinkedList<>();

        if (((VariableDeclarator) declarationNode).getInitializer().isPresent()
                && !((VariableDeclarator) declarationNode).getInitializer().get().isNullLiteralExpr()) {
            lastAssignedNodes.add(((VariableDeclarator) declarationNode).getInitializer().get());
        }

        getAssignExprsInMethod(nameExpr, declarationNode, lastAssignedNodes,
                nameExprPos, nameExprParentMethod, nameExprParentMethod);

        System.out.println("Last assigned nodes: " + lastAssignedNodes);

        return lastAssignedNodes;
    }

    private static void getAssignExprsInMethod(NameExpr nameExpr, Node declarationNode, List<Node> lastAssignedNodes,
                                                  int position, Node parentMethod, Node node) {
        if (node instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) node;

            if (assignExpr.getBegin().isPresent()) {
                /*
                if (assignExpr.getBegin().get().line >= position) {
                    return;
                }
                */

                if (assignExpr.getTarget().equals(nameExpr)) {
                    System.out.println("Found assignment matching nameExpr: " + assignExpr);
                    try {
                        ResolvedValueDeclaration resolvedValueDeclaration = nameExpr.resolve();
                        /*
                        if (resolvedValueDeclaration instanceof JavaParserSymbolDeclaration
                                && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                                instanceof VariableDeclarator
                                && ((JavaParserSymbolDeclaration) resolvedValueDeclaration).getWrappedNode()
                                .equals(declarationNode)) {
                            lastAssignedNodes.add(((AssignExpr) node).getValue());
                        }
                        */
                        lastAssignedNodes.add(((AssignExpr) node).getValue());
                    } catch (Exception e) {
                        System.out.println("ASSIGNMENTLOCATOR: Error resolving NameExpr: " + e);
                    }
                }
            }
        } else {
            for (Node child : node.getChildNodes()) {
                getAssignExprsInMethod(nameExpr, declarationNode, lastAssignedNodes, position, parentMethod, child);
            }
        }
    }

    /*
     * Get the depth level of any node in a specified MethodDeclaration (methodNode)
     */
    private static int levelOfIf(Node node, Node methodNode, int level) {
        if (node.equals(methodNode)) {
            return level;
        }

        if (node instanceof Statement && ((Statement) node).isIfStmt()) {
            if (node.getParentNode().isPresent()) {
                while (node.getParentNode().isPresent() && node instanceof Statement && ((Statement) node).isIfStmt()) {
                    node = node.getParentNode().get();
                }
                return levelOfIf(node.getParentNode().get(), methodNode, level + 1);
            } else {
                return level + 1;
            }
        } else {
            if (node.getParentNode().isPresent()) {
                return levelOfIf(node.getParentNode().get(), methodNode, level);
            } else {
                return level;
            }
        }
    }

    /*
     * Returns true if the argument node is located in a "else" block
     */
    private static boolean locatedInElseBlock(Node node, Node methodNode) {
        IfStmt ifStmt = getParentIfStmt(node, methodNode);
        if (ifStmt != null && ifStmt.getElseStmt().isPresent()) {
            Node elseBlock = ifStmt.getElseStmt().get();
            return checkIfInElseBlock(node, elseBlock);
        } else {
            return false;
        }
    }

    private static boolean checkIfInElseBlock(Node nodeToFind, Node node) {
        if (node.equals(nodeToFind)) {
            return true;
        } else {
            for (Node child : node.getChildNodes()) {
                if (checkIfInElseBlock(nodeToFind, child)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static IfStmt getParentIfStmt(Node node, Node methodNode) {
        if (node.equals(methodNode)) {
            return null;
        }

        if (node instanceof Statement && ((Statement) node).isIfStmt()) {
            return ((Statement) node).asIfStmt();
        } else {
            return getParentIfStmt(node.getParentNode().get(), methodNode);
        }
    }
}
