//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 17.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

public class JSONRoot {

    public String path;
    public String className;
    public String methodName;
    public String scopeName;
    public JSONObject jsonObject;

    public JSONRoot(String path, String className, String methodName, String scopeName) {
        this.path = path;
        this.className = className;
        this.methodName = methodName;
        this.scopeName = scopeName;
    }

    public String getIdentifier() {
        return path + "&" + className + "&" + methodName + "&" + scopeName;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Path: \n" + this.path + "\n");
        stringBuilder.append("Class name: \n" + this.className + "\n");
        stringBuilder.append("Method name: \n" + this.methodName + "\n");
        stringBuilder.append("Scope name: \n" + this.scopeName + "\n");
        stringBuilder.append("JSON Object: \n" + this.jsonObject + "\n");
        stringBuilder.append("=========================JSONRoot=========================\n");
        return stringBuilder.toString();
    }

}
