//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

import java.util.HashMap;

public class APIURL {

    public String scheme;
    public String authority;
    public HashMap<String, APIEndpoint> endpoints = new HashMap<>();

    public APIURL(String scheme) {
        this.scheme = scheme;
    }

    public String getBaseURL() {
        return scheme + authority;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Scheme: " + this.scheme + "\n");
        stringBuilder.append("Authority: " + this.authority + "\n");
        stringBuilder.append("Base URL: " + getBaseURL() + "\n");
        stringBuilder.append("Endpoints: \n");
        for (APIEndpoint endpoint : this.endpoints.values()) {
            stringBuilder.append(endpoint.toString());
        }
        stringBuilder.append("=========================API URL=========================\n");
        return stringBuilder.toString();
    }

}
