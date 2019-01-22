//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 26.09.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

public class Snippet {

    public String classCode;
    public String name;
    public String path;
    public String methodCode;
    public String networkingCode;
    public String library;
    public int startLine;
    public int endLine;
    public String type;

    public Snippet(String path, String name, String classCode, String methodCode, String networkingCode, String library, int startLine, int endLine, String type) {
        this.path = path;
        this.name = name;
        this.classCode = classCode;
        this.methodCode = methodCode;
        this.networkingCode = networkingCode;
        this.library = library;
        this.startLine = startLine;
        this.endLine = endLine;
        this.type = type;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Name: \n" + this.name + "\n");
        stringBuilder.append("Path: \n" + this.path + "\n");
        stringBuilder.append("Library: \n" + this.library + "\n");
        stringBuilder.append("Networking code: \n" + this.networkingCode + "\n");
        stringBuilder.append("Lines: \n[" + this.startLine + ", " + this.endLine + "] \n");
        stringBuilder.append("Type: \n" + this.type + "\n");
        stringBuilder.append("Class code: \n" + this.classCode + "\n");
        stringBuilder.append("Method code: \n" + this.methodCode + "\n");
        stringBuilder.append("=========================Snippet=========================\n");
        return stringBuilder.toString();
    }

    public String minimalStringRepresentation() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Name: \n" + this.name + "\n");
        stringBuilder.append("Path: \n" + this.path + "\n");
        stringBuilder.append("Library: \n" + this.library + "\n");
        stringBuilder.append("Networking code: \n" + this.networkingCode + "\n");
        stringBuilder.append("Lines: \n[" + this.startLine + ", " + this.endLine + "] \n");
        stringBuilder.append("Type: \n" + this.type + "\n");
        stringBuilder.append("=========================Snippet=========================\n");
        return stringBuilder.toString();
    }

}
