//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.*;
import com.marctarnutzer.jandrolyzer.Models.APIEndpoint;
import com.marctarnutzer.jandrolyzer.Models.APIURL;
import com.marctarnutzer.jandrolyzer.Project;
import com.marctarnutzer.jandrolyzer.Utils;

import java.util.*;

public class APIURLStrategy {

    public boolean extract(String potentialURL, Project project) {
        String urlScheme = getScheme(potentialURL);
        if (urlScheme == null) {
            return false;
        }

        APIURL apiurl = new APIURL(urlScheme);
        potentialURL = potentialURL.replaceFirst(urlScheme, "");

        potentialURL = extractAuthority(potentialURL, apiurl);

        if (potentialURL == null) {
            return false;
        } else if (potentialURL.equals("")) {
            addAPIURLToProject(project, apiurl.getBaseURL(), apiurl);
            return true;
        }

        extractEndpoint(potentialURL, apiurl);

        addAPIURLToProject(project, apiurl.getBaseURL(), apiurl);
        return true;
    }

    public boolean extract(BinaryExpr binaryExpr, Project project) {
        if (!binaryExpr.getParentNode().isPresent() || binaryExpr.getParentNode().get() instanceof BinaryExpr) {
            return false;
        }

        System.out.println("Found top level BinaryExpr: " + binaryExpr);

        List<String> serializedBinaryExprs = serializeBinaryExpr(binaryExpr);

        if (serializedBinaryExprs == null) {
            return false;
        }

        boolean foundValidURL = false;

        for (String serializedBinaryExpr : serializedBinaryExprs) {
            foundValidURL = extract(serializedBinaryExpr, project) || foundValidURL;
        }

        return foundValidURL;
    }

    // Check if a new StringBuilder object is created and check if it contains API endpoint information
    public boolean extract(VariableDeclarator variableDeclarator, Project project) {
        if (variableDeclarator.getInitializer().isPresent() && variableDeclarator.getInitializer().get()
                .isObjectCreationExpr()) {
            if (variableDeclarator.getInitializer().get().asObjectCreationExpr().getType().getName().asString()
                    .equals("StringBuilder")) {
                return extractStringBuilderValue(variableDeclarator, project);
            }
        }

        return false;
    }

    private boolean extractStringBuilderValue(VariableDeclarator variableDeclarator, Project project) {
        Node containingNode = Utils.getParentClassOrMethod(variableDeclarator);

        List<String> potentialApiURLs = new LinkedList<>();
        if (containingNode instanceof MethodDeclaration) {
            List<MethodCallExpr> methodCallExprs = containingNode.findAll(MethodCallExpr.class);
            potentialApiURLs = reconstructStringBuilderStringsIn(methodCallExprs, variableDeclarator);
        } else if (containingNode instanceof ClassOrInterfaceDeclaration) {
            if (variableDeclarator.getParentNode().isPresent() && variableDeclarator.getParentNode().get()
                    instanceof FieldDeclaration) {
                Node fieldNode = variableDeclarator.getParentNode().get();

                List<MethodDeclaration> methodDeclarations = containingNode.findAll(MethodDeclaration.class);
                for (MethodDeclaration methodDeclaration : methodDeclarations) {
                    List<MethodCallExpr> methodCallExprs = methodDeclaration.findAll(MethodCallExpr.class);
                    potentialApiURLs.addAll(reconstructStringBuilderStringsIn(methodCallExprs, fieldNode));
                }
            }
        }

        /*
         * Check if the assembled StringBuilder strings contain valid API URLs
         */
        boolean foundAPIURL = false;
        for (String potentialAPIURL : potentialApiURLs) {
            foundAPIURL = extract(potentialAPIURL, project) || foundAPIURL;
        }

        return foundAPIURL;
    }

    private List<String> reconstructStringBuilderStringsIn(List<MethodCallExpr> methodCallExprs,
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
                if (appendValues.isEmpty()) {
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

    private List<String> serializeBinaryExpr(BinaryExpr binaryExpr) {
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
        }

        return toReturn;
    }

    private List<String> getExpressionValue(Expression expression) {
        System.out.println("Getting value of expression: " + expression);

        if (expression.isNameExpr()) {
            ResolvedValueDeclaration resolvedValueDeclaration;
            try {
                resolvedValueDeclaration = expression.asNameExpr().resolve();
            } catch (Exception e) {
                System.out.println("Error resolving NameExpr: " + e);
                return null;
            }

            if (resolvedValueDeclaration.isVariable()) {
                // TODO: Check if I even need this...
                System.out.println("Found a variable");
            } else if (resolvedValueDeclaration.isField()) {
                if (resolvedValueDeclaration.asField().getType().isReferenceType()) {
                    if (resolvedValueDeclaration.asField().getType().asReferenceType().getQualifiedName()
                            .equals("java.lang.String")) {
                        Node declarationNode = ((JavaParserFieldDeclaration) resolvedValueDeclaration.asField())
                                .getWrappedNode();
                        if (((FieldDeclaration) declarationNode).asFieldDeclaration().getVariables().size() == 1) {
                            VariableDeclarator variableDeclarator = ((FieldDeclaration) declarationNode)
                                    .asFieldDeclaration().getVariables().get(0);
                            if (variableDeclarator.getInitializer().isPresent()) {
                                if (variableDeclarator.getInitializer().get().isStringLiteralExpr()) {
                                    return Arrays.asList(variableDeclarator.getInitializer().get().asStringLiteralExpr().getValue());
                                }
                            }
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

                List<MethodCallExpr> methodCallExprs = methodDeclaration.findCompilationUnit().get().findAll(MethodCallExpr.class);
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
                            List<String> expressionValues = getExpressionValue(methodCallExpr.getArgument(parameterPosition));
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
                        return getExpressionValue(((VariableDeclarator) declarationNode).getInitializer().get());
                    }
                }
            }
        } else if (expression.isStringLiteralExpr()) {
            return Arrays.asList(expression.asStringLiteralExpr().getValue());
        } else if (expression.isBinaryExpr()) {
            return serializeBinaryExpr(expression.asBinaryExpr());
        }

        return null;
    }

    private void addAPIURLToProject(Project project, String baseURL, APIURL apiurl) {
        if (project.apiURLs.containsKey(baseURL)) {
            if (apiurl.endpoints.isEmpty()) {
                return;
            }

            APIEndpoint apiEndpoint = apiurl.endpoints.entrySet().iterator().next().getValue();
            if (project.apiURLs.get(baseURL).endpoints.containsKey(apiEndpoint.path)) {
                for (Map.Entry<String, String> queryEntry : apiEndpoint.queries.entrySet()) {
                    if (!project.apiURLs.get(baseURL).endpoints.get(apiEndpoint.path).queries
                            .containsKey(queryEntry.getKey())) {
                        project.apiURLs.get(baseURL).endpoints.get(apiEndpoint.path)
                                .queries.put(queryEntry.getKey(), queryEntry.getValue());
                    }
                }
            } else {
                project.apiURLs.get(baseURL).endpoints.put(apiEndpoint.path, apiEndpoint);
            }
        } else {
            project.apiURLs.put(baseURL, apiurl);
        }
    }

    private void extractQuery(String queryString, APIEndpoint apiEndpoint) {
        String[] queryPairs = queryString.split("&");

        if (queryPairs.length == 0) {
            return;
        }

        for (String keyValuePairString : queryPairs) {
            String[] keyValuePair = keyValuePairString.split("=");
            if (keyValuePair.length == 2) {
                apiEndpoint.queries.put(keyValuePair[0], keyValuePair[1]);
            }
        }
    }

    /*
     * Extracts the endpoint path & query key value pairs and assigns their values to the APIURL object
     */
    private void extractEndpoint(String endpointString, APIURL apiurl) {
        String[] urlParts = endpointString.split("\\?");

        if (urlParts.length == 0) {
            return;
        }

        String endpointPath = urlParts[0];
        if (endpointPath.endsWith("/")) {
            endpointPath = endpointPath.substring(0, endpointPath.length() - 1);
        }

        String toReturn = endpointString.replaceFirst(urlParts[0], "");
        toReturn = toReturn.replaceFirst("\\?", "");

        APIEndpoint apiEndpoint = new APIEndpoint(endpointPath);
        apiurl.endpoints.put(endpointPath, apiEndpoint);

        extractQuery(toReturn, apiEndpoint);
    }

    /*
     * Extracts URL authority and assigns the value to the APIURL object
     * Returns potential endpoint path + potential query string or null in case of invalid authority format
     */
    private String extractAuthority(String urlString, APIURL apiurl) {
        String[] urlParts = urlString.split("/");

        if (urlParts.length == 0 || urlParts[0].length() == 0) {
            return null;
        }

        apiurl.authority = urlParts[0];

        if (urlString.replaceFirst(urlParts[0] + "/", "") == urlString) {
            return urlString.replaceFirst(urlParts[0], "");
        }

        return urlString.replaceFirst(urlParts[0] + "/", "");
    }

    /*
     * Returns a valid URL scheme or null if no valid URL scheme was detected
     */
    private String getScheme(String potentialURL) {
        if (potentialURL.startsWith("https://")) {
            return "https://";
        } else if (potentialURL.startsWith("www.") || potentialURL.startsWith("http://")) {
            return "http://";
        } else if (potentialURL.startsWith("ws://")) {
            return "ws://";
        } else if (potentialURL.startsWith("wss://")) {
            return "wss://";
        }

        return null;
    }

}
