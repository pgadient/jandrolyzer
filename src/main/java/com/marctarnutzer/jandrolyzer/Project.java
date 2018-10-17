package com.marctarnutzer.jandrolyzer;

import java.util.LinkedList;

public class Project {

    public String name;
    public String path;
    public LinkedList<Snippet> snippets = new LinkedList<Snippet>();

    public Project(String path, String name) {
        this.path = path;
        this.name = name;
    }

    public void addSnippet(Snippet snippet) {
        this.snippets.add(snippet);
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
