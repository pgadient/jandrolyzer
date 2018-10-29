//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 05.10.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class GradleParser extends CodeVisitorSupport {

    private String pathToGradle;
    private String gradleSrc;
    private HashSet<String> libraries;
    private HashMap<String, String> foundLibraries = new HashMap<>();

    public GradleParser(String pathToGradle, HashSet<String> libraries) throws IOException {
        this.pathToGradle = pathToGradle;
        this.libraries = libraries;

        this.gradleSrc = new String(Files.readAllBytes(Paths.get(this.pathToGradle)));

        removeImports();
    }

    public HashMap<String, String> parse() {
        AstBuilder astBuilder = new AstBuilder();

        List<ASTNode> astNodes = null;

        try {
            astNodes = astBuilder.buildFromString(gradleSrc);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            System.out.println("Path: " + pathToGradle);
            return this.foundLibraries;
        }

        for (ASTNode astNode : astNodes) {
            astNode.visit(this);
        }

        return this.foundLibraries;
    }

    @Override
    public void visitArgumentlistExpression(ArgumentListExpression argumentListExpression) {

        /*
        for (Expression expression : argumentListExpression.getExpressions()) {
            if (expression instanceof GStringExpression) {
                System.out.println("GStringExpression: " + ((GStringExpression) expression).getText());
                System.out.println("Verbatim text: " + ((GStringExpression) expression).getText());
                System.out.println("Complete text: " + ((GStringExpression) expression).toString());

                if (((GStringExpression) expression).getValues().size() > 0) {

                }

            }
        }
        */


        String[] tokens = argumentListExpression.getText().split(":");

        if (tokens.length >= 1) {
            String library = tokens[0].substring(1);
            if (tokens.length == 1) {
                library = library.substring(0, library.length() - 1);
            }
            if (libraries.contains(library)) {
                if (tokens.length == 3) {
                    this.foundLibraries.put(library, tokens[2].substring(0, tokens[2].length() - 1));
                } else if (library.contains("org.apache.http.legacy")) {
                    this.foundLibraries.put("org.apache.http.legacy", "0");
                }
            }
        }

        super.visitArgumentlistExpression(argumentListExpression);
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression methodCallExpression) {
        String methodString = methodCallExpression.getMethodAsString();
        if (methodString != null) {
            if (methodCallExpression.getMethodAsString().equals("compileSdkVersion")) {
                String sdkVersion = methodCallExpression.getArguments().getText();
                this.foundLibraries.put("android.core", sdkVersion.substring(1, sdkVersion.length() - 1));
            }

        }
        super.visitMethodCallExpression(methodCallExpression);
    }

    private void removeImports() throws IOException {
        HashSet<String> imports = new HashSet<>();

        BufferedReader lineReader = new BufferedReader(new StringReader(this.gradleSrc));
        String src = "";
        String line = null;
        while ((line = lineReader.readLine()) != null) {
            if (line.startsWith("import ")) {
                String[] tokens = line.split("\\.");
                imports.add(tokens[tokens.length - 1]);
            } else {
                src = src + line + System.lineSeparator();
            }
        }

        String[] tokens = src.split("((?<=\\{)|(?=\\}))");

        String cleanSrc = "";
        for (String token : tokens) {
            String[] t = token.split("[\\s(]");
            Boolean contains = false;
            for (String s : t) {
                if (imports.contains(s)) {
                    contains = true;
                }
            }

            if (!contains) {
                cleanSrc = cleanSrc + token;
            }
        }

        this.gradleSrc = cleanSrc;
    }
}
