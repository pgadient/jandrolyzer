//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 28.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

import java.util.HashMap;
import java.util.Map;

public class RequestResponse {

    public String url;
    public String httpMethod;
    public int code;
    public boolean success;
    public String errorMessage;
    public String response;
    public String jsonString;
    public Map<String, String> headers = new HashMap<>();

    public RequestResponse(String url, String httpMethod, boolean success, String errorMessage, String response,
                           String jsonString, int code) {
        this.url = url;
        this.httpMethod = httpMethod;
        this.success = success;
        this.errorMessage = errorMessage;
        this.response = response;
        this.jsonString = jsonString;
        this.code = code;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("URL: \n" + this.url+ "\n");
        stringBuilder.append("HTTP method: \n" + this.httpMethod + "\n");
        stringBuilder.append("Code: \n" + this.code + "\n");
        stringBuilder.append("JSON string: \n" + this.jsonString + "\n");
        stringBuilder.append("Success: \n" + this.success + "\n");
        stringBuilder.append("Error message: \n" + this.errorMessage + "\n");
        stringBuilder.append("Response: \n" + this.response + "\n");
        stringBuilder.append("Headers: \n");
        for (Map.Entry<String, String> entry : this.headers.entrySet()) {
            stringBuilder.append("Key: " + entry.getKey() + ", value: " + entry.getValue() + "\n");
        }
        stringBuilder.append("=========================RequestResponse=========================\n");
        return stringBuilder.toString();
    }

}
