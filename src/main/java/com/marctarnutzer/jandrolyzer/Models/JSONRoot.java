//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 17.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

public class JSONRoot {

    public String path;
    public String className;
    public String methodName;
    public String scopeName;
    public String salt;
    public String library;
    public JSONObject jsonObject;

    public JSONRoot(String path, String className, String methodName, String scopeName) {
        this.path = path;
        this.className = className;
        this.methodName = methodName;
        this.scopeName = scopeName;
    }

    //TODO: Maybe change to path & className & [the whole class, constructor, method, etc. chain separated by &]
    public String getIdentifier() {
        return path + "&" + className + "&" + methodName + "&" + scopeName + "&" + salt;
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

    public String formatJSON() {
        return this.jsonObject.formatJSON();
    }

    public String formatJSONWithoutValues() {
        return this.jsonObject.formatJSONWithoutValues();
    }

    public String logInfoAndJSON() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Path: \n" + this.path + "\n");
        stringBuilder.append("JSON library: \n" + this.library + "\n");
        stringBuilder.append("Class name: \n" + this.className + "\n");
        stringBuilder.append("Method name: \n" + this.methodName + "\n");
        stringBuilder.append("Scope name: \n" + this.scopeName + "\n");
        stringBuilder.append("JSON: \n" + this.jsonObject.formatJSON() + "\n");
        stringBuilder.append("=========================JSONModel=========================\n");
        return stringBuilder.toString();
    }

}
