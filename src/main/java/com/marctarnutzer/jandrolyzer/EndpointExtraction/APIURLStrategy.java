//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.marctarnutzer.jandrolyzer.Models.APIEndpoint;
import com.marctarnutzer.jandrolyzer.Models.APIURL;
import com.marctarnutzer.jandrolyzer.Project;

import java.util.Map;

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

        String serializedBinaryExpr = serializeBinaryExpr(binaryExpr);

        if (serializedBinaryExpr == null) {
            return false;
        }

        return extract(serializedBinaryExpr, project);
    }

    private String serializeBinaryExpr(BinaryExpr binaryExpr) {
        String toReturn = null;

        if (!binaryExpr.getOperator().asString().equals("+")) {
            return null;
        }

        Expression leftExpression = binaryExpr.getLeft();
        Expression rightExpression = binaryExpr.getRight();

        if (leftExpression.isBinaryExpr()) {
            toReturn = serializeBinaryExpr(leftExpression.asBinaryExpr());
        } else if (leftExpression.isStringLiteralExpr()) {
            toReturn = leftExpression.asStringLiteralExpr().getValue();
        } else if (leftExpression.isNameExpr()) {
            toReturn = getExpressionValue(leftExpression.asNameExpr());
        }

        if (rightExpression.isBinaryExpr()) {
            toReturn = toReturn + serializeBinaryExpr(rightExpression.asBinaryExpr());
        } else if (rightExpression.isStringLiteralExpr()) {
            toReturn = toReturn + rightExpression.asStringLiteralExpr().getValue();
        } else if (rightExpression.isNameExpr()) {
            toReturn = getExpressionValue(rightExpression.asNameExpr());
        }

        System.out.println("toReturn: " + toReturn);

        return toReturn;
    }

    private String getExpressionValue(Expression expression) {
        if (expression.isNameExpr()) {
            ResolvedValueDeclaration resolvedValueDeclaration;
            try {
                resolvedValueDeclaration = expression.asNameExpr().resolve();
            } catch (Exception e) {
                return null;
            }

            if (resolvedValueDeclaration.isVariable()) {
                System.out.println("Its a variable");
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
                                    return variableDeclarator.getInitializer().get().asStringLiteralExpr().getValue();
                                }
                            }
                        }
                    }
                }
            } else if (resolvedValueDeclaration.isParameter()) {
                System.out.println("Its a parameter");
            }
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
