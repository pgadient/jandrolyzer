//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.marctarnutzer.jandrolyzer.Models.APIEndpoint;
import com.marctarnutzer.jandrolyzer.Models.APIURL;
import com.marctarnutzer.jandrolyzer.Project;

import java.util.*;

public class APIURLStrategy {

    Project project;

    public APIURLStrategy(Project project) {
        this.project = project;
        ExpressionValueExtraction.project = project;
    }

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

        List<String> serializedBinaryExprs = ExpressionValueExtraction.serializeBinaryExpr(binaryExpr);

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
                /*
                 * Check if the assembled StringBuilder strings contain valid API URLs
                 */

                List<String> potentialApiURLs = ExpressionValueExtraction.extractStringBuilderValue(variableDeclarator, project);

                boolean foundAPIURL = false;
                for (String potentialAPIURL : potentialApiURLs) {
                    foundAPIURL = extract(potentialAPIURL, project) || foundAPIURL;
                }

                return foundAPIURL;
            }
        }

        return false;
    }

    /*
     * Extract API URLs from concatenated Strings using the concat() MethodCallExpr
     */
    public void extract(MethodCallExpr methodCallExpr, Project project) {
        if (methodCallExpr.getParentNode().isPresent() && methodCallExpr.getParentNode().get() instanceof MethodCallExpr
                && ((MethodCallExpr) methodCallExpr.getParentNode().get()).asMethodCallExpr().getName().asString()
                .equals("concat")) {
            return;
        }

        System.out.println("Detected rightmost concat method: " + methodCallExpr);

        List<String> stringsToCheck = ExpressionValueExtraction.extractStringConcatValue(methodCallExpr);

        if (stringsToCheck == null) {
            return;
        }

        for (String stringToCheck : stringsToCheck) {
            extract(stringToCheck, project);
        }
    }

    /*
     * Extract API URLs from java.net.URL ObjectCreationExpr and save valid URLs in Project object
     */
    public void extract(ObjectCreationExpr objectCreationExpr, Project project) {
        List<String> toCheck = ExpressionValueExtraction.extractURLValue(objectCreationExpr);

        if (toCheck == null) {
            return;
        }

        for (String tc : toCheck) {
            extract(tc, project);
        }
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

    private void extractFragment(String fragmentString, APIEndpoint apiEndpoint) {
        String[] fragments = fragmentString.split("#");

        for (String fragment : fragments) {
            apiEndpoint.fragments.add(fragment);
        }
    }

    private void extractQuery(String queryString, APIEndpoint apiEndpoint) {
        if (queryString.startsWith("?")) {
            queryString = queryString.replaceFirst("\\?", "");

            String[] qf = queryString.split("#");

            String[] queryPairs = qf[0].split("&");

            if (queryPairs.length == 0 || qf.length == 0) {
                return;
            }

            for (String keyValuePairString : queryPairs) {
                String[] keyValuePair = keyValuePairString.split("=");
                if (keyValuePair.length == 2) {
                    apiEndpoint.queries.put(keyValuePair[0], keyValuePair[1]);
                }
            }

            if (qf.length > 1) {
                extractFragment(queryString.replaceFirst(qf[0] + "#", ""), apiEndpoint);
            }
        } else if (queryString.startsWith("#")) {
            extractFragment(queryString.replaceFirst("#", ""), apiEndpoint);
        }
    }

    /*
     * Extracts the endpoint path & query key value pairs and assigns their values to the APIURL object
     */
    private void extractEndpoint(String endpointString, APIURL apiurl) {
        String[] urlParts = endpointString.split("(\\?|#)");

        if (urlParts.length == 0) {
            return;
        }

        String endpointPath = urlParts[0];
        if (endpointPath.endsWith("/")) {
            endpointPath = endpointPath.substring(0, endpointPath.length() - 1);
        }

        String possibleQueryOrFragment = endpointString.replaceFirst(urlParts[0], "");
        //possibleQueryOrFragment = possibleQueryOrFragment.replaceFirst("(\\?|#)", "");

        APIEndpoint apiEndpoint = new APIEndpoint(endpointPath);
        apiurl.endpoints.put(endpointPath, apiEndpoint);

        extractQuery(possibleQueryOrFragment, apiEndpoint);
    }

    /*
     * Extracts URL authority and assigns the value to the APIURL object
     * Returns potential endpoint path + potential query string or null in case of invalid authority format
     */
    private String extractAuthority(String urlString, APIURL apiurl) {
        String[] urlParts = urlString.split("(/|\\?|#)");

        if (urlParts.length == 0 || urlParts[0].length() == 0) {
            return null;
        }

        apiurl.authority = urlParts[0];


        if (urlString.replaceFirst(urlParts[0] + "/", "").equals(urlString)) {
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
