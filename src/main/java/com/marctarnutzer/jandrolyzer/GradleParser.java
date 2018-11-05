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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleParser extends CodeVisitorSupport {

    private String pathToGradle;
    private String gradleSrc;
    private HashSet<String> libraries;
    private HashMap<String, String> foundLibraries = new HashMap<>();

    public GradleParser(String pathToGradle, HashSet<String> libraries) throws IOException {
        this.pathToGradle = pathToGradle;
        this.libraries = libraries;

        this.gradleSrc = new String(Files.readAllBytes(Paths.get(this.pathToGradle)));

        cleanGradle();
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

    private class Token {
        public Boolean marked;
        public String content;
        public Token parent;
        public List<Token> children = new LinkedList<>();

        public Token(Boolean marked, String content, Token parent) {
            this.marked = marked;
            this.content = content;
            this.parent = parent;
        }
    }

    // Cleans the Gradle file from imports and other unnecessary parts
    private void cleanGradle() throws IOException {
        HashSet<String> toRemove = new HashSet<>();

        toRemove.add("ComponentSelection");
        toRemove.add("MavenDeployment");
        toRemove.add("InvalidUserDataException");
        toRemove.add("GradleException");
        toRemove.add("FileCollection");
        toRemove.add("StandardOutputListener");
        toRemove.add("XmlSlurper");
        toRemove.add("org.gradle.plugins.ide.eclipse.model.SourceFolder");
        toRemove.add("org.gradle.plugins.ide.eclipse.model.Library");
        toRemove.add("com.googlecode.htmlcompressor.compressor.HtmlCompressor");
        toRemove.add("com.google.javascript.jscomp.CompilerOptions");
        toRemove.add("net.evendanan.versiongenerator.generators.EnvBuildVersionGenerator.CircleCi");
        toRemove.add("net.evendanan.versiongenerator.generators.GitBuildVersionGenerator");
        toRemove.add("net.evendanan.versiongenerator.generators.StaticVersionGenerator");

        BufferedReader lineReader = new BufferedReader(new StringReader(this.gradleSrc));
        String src = "";
        String line = null;
        while ((line = lineReader.readLine()) != null) {
            if (line.startsWith("import ")) {
                String[] tokens = line.split("\\.");
                toRemove.add(tokens[tokens.length - 1]);
            } else {
                src = src + line + System.lineSeparator();
            }
        }

        Pattern pattern = Pattern.compile("\\$\\{([^\\}]+)\\}");
        Matcher matcher = pattern.matcher(src);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(stringBuffer, "UNKNOWN_VARIABLE_VALUE");
        }
        matcher.appendTail(stringBuffer);
        src = stringBuffer.toString();

        String[] tokens = src.split("((?=\\{)|(?=\\}))");

        Token sentinel = new Token(false, "", null);
        Token lastToken = sentinel;
        String cleanSrc = "";
        for (String token : tokens) {
            String[] t = token.split("[\\s|(]");
            Boolean shouldMark = false;
            for (String s : t) {
                if (toRemove.contains(s)) {
                    shouldMark = true;
                }
            }

            Token tk = null;
            if (token.startsWith("}")) {
                tk = new Token(shouldMark, token, lastToken.parent.parent);
            } else {
                tk = new Token(shouldMark, token, lastToken);
            }

            for (Token neighbor : tk.parent.children) {
                if (neighbor.marked) {
                    tk.marked = true;
                }
            }
            tk.marked = tk.parent.marked || tk.marked;

            tk.parent.children.add(tk);
            lastToken = tk;

            if (tk.marked) {
                if (token.startsWith("{") && !tk.parent.marked) {
                    cleanSrc = cleanSrc + token.substring(0, 1);
                } else if (token.startsWith("}") && !tk.parent.marked &&
                        !tk.parent.children.get(tk.parent.children.size() - 2).marked) {
                    cleanSrc = cleanSrc + token.substring(0, 1);
                }
            } else {
                cleanSrc = cleanSrc + token;
            }
        }

        this.gradleSrc = cleanSrc;
    }
}
