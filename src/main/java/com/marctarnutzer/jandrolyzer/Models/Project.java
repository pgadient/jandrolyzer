//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 26.09.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

import com.github.javaparser.ast.CompilationUnit;

import java.util.*;

public class Project {

    public String name;
    public String path;
    public LinkedList<Snippet> snippets = new LinkedList<Snippet>();
    public Map<String, JSONRoot> jsonModels = new HashMap<>();
    public Set<String> jsonLibraries = new HashSet<>();
    public Map<String, APIURL> apiURLs = new HashMap<>();
    public List<CompilationUnit> compilationUnits = new LinkedList<>();

    public Project(String path, String name) {
        this.path = path;
        this.name = name;
    }

    public void addSnippet(Snippet snippet) {
        this.snippets.add(snippet);
    }

    public void addLibrary(String library) {
        this.jsonLibraries.add(library);
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Name: \n" + this.name + "\n");
        stringBuilder.append("Path: \n" + this.path + "\n");
        stringBuilder.append("Snippets: \n");
        for (Snippet snippet : this.snippets) {
            stringBuilder.append(snippet.toString());
        }
        stringBuilder.append("=========================Project=========================\n");
        return stringBuilder.toString();
    }

    public String minimalStringRepresentation() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Name: \n" + this.name + "\n");
        stringBuilder.append("Path: \n" + this.path + "\n");
        stringBuilder.append("Snippets: \n");
        for (Snippet snippet : this.snippets) {
            stringBuilder.append(snippet.minimalStringRepresentation());
        }
        stringBuilder.append("=========================Project=========================\n");
        return stringBuilder.toString();
    }

}
