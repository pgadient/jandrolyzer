//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.marctarnutzer.jandrolyzer.Models.APIEndpoint;
import com.marctarnutzer.jandrolyzer.Models.APIURL;

public class APIURLStrategy {

    public boolean extract(String potentialURL) {
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
            // TODO: Add to API URL list
            return true;
        }

        potentialURL = extractEndpoint(potentialURL, apiurl);

        // TODO: Add to API URL list
        return true;
    }

    private void extractQuery(String queryString, APIEndpoint apiEndpoint) {
        String[] queryPairs = queryString.split("&");

        if (queryPairs.length == 0) {
            return;
        }

        for (String keyValuePairString : queryPairs) {
            String[] keyValuePair = keyValuePairString.split("=");
            if (keyValuePair.length == 2) {
                apiEndpoint.queries.put(keyValuePair[0], keyValuePair[0]);
            }
        }
    }

    /*
     * Extracts the endpoint path & query key value pairs and assigns their values to the APIURL object
     * Returns potential query string or null in case of invalid endpoint path format
     */
    private String extractEndpoint(String endpointString, APIURL apiurl) {
        String[] urlParts = endpointString.split("\\?");

        if (urlParts.length == 0) {
            return null;
        }

        APIEndpoint apiEndpoint = new APIEndpoint(urlParts[0]);
        apiurl.endpoints.add(apiEndpoint);

        String toReturn = endpointString.replaceFirst(urlParts[0] + "?", "");
        extractQuery(toReturn, apiEndpoint);

        return toReturn;
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
