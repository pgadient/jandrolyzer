//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 26.09.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package ch.unibe.scg.jandrolyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;
import com.github.javaparser.utils.ParserCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;
import ch.unibe.scg.jandrolyzer.APIAnalysis.VariableCollector;
import ch.unibe.scg.jandrolyzer.EndpointExtraction.APIURLStrategy;
import ch.unibe.scg.jandrolyzer.EndpointExtraction.OkHttpStrategy;
import ch.unibe.scg.jandrolyzer.EndpointExtraction.RetrofitStrategy;
import ch.unibe.scg.jandrolyzer.EndpointExtraction.StringValueExtraction;
import ch.unibe.scg.jandrolyzer.Models.APIURL;
import ch.unibe.scg.jandrolyzer.Models.JSONRoot;
import ch.unibe.scg.jandrolyzer.Models.Project;
import ch.unibe.scg.jandrolyzer.Models.Snippet;
import ch.unibe.scg.jandrolyzer.RequestStructureExtraction.JSONStringStrategy;
import ch.unibe.scg.jandrolyzer.RequestStructureExtraction.MoshiGSONStrategy;
import ch.unibe.scg.jandrolyzer.RequestStructureExtraction.ORGJSONStrategy;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class ProjectAnalyzer {

    private Project project;
    private File projectFolder;
    private Map<String, HashSet<String>> libraries;
    private CombinedTypeSolver combinedTypeSolver;
    private List<Project> projects;
    private int totalProjects;
    private boolean enableSymbolSolving = true;
    private boolean shouldPrintAST = true;
    private String libraryFolderPath;
    private ORGJSONStrategy orgjsonStrategy = new ORGJSONStrategy();
    private Map<String, JSONRoot> jsonModels = new HashMap<>();
    private MoshiGSONStrategy moshiGsonStrategy;
    private JSONStringStrategy jsonStringStrategy = new JSONStringStrategy();
    private APIURLStrategy apiurlStrategy;
    private OkHttpStrategy okHttpStrategy;
    private RetrofitStrategy retrofitStrategy;

    public ProjectAnalyzer(String path, Map<String, HashSet<String>> libraries, List<Project> projects,
                           int totalProjects, String libraryFolderPath) throws FileNotFoundException {
        this.libraries = libraries;
        this.projects = projects;
        this.totalProjects = totalProjects;
        this.libraryFolderPath = libraryFolderPath;

        this.projectFolder = new File(path);
        if (!projectFolder.exists() || !projectFolder.isDirectory()) {
            throw new FileNotFoundException("Specified project directory does not exist or is not a directory");
        }
        this.project = new Project(this.projectFolder.getPath(), this.projectFolder.getName());
        this.apiurlStrategy = new APIURLStrategy(project);
        this.okHttpStrategy = new OkHttpStrategy(project, apiurlStrategy, jsonStringStrategy);
        this.moshiGsonStrategy = new MoshiGSONStrategy(project);
        this.retrofitStrategy = new RetrofitStrategy(project, apiurlStrategy, moshiGsonStrategy);
        this.project.jsonModels = jsonModels;
    }

    public void analyze() {
        System.out.println("Analyzing project: " + this.project.path);

        ProjectRoot projectRoot;
        if (enableSymbolSolving) {
            this.combinedTypeSolver = new CombinedTypeSolver();
            //this.combinedTypeSolver.add(new JavaParserTypeSolver(projectFolder.toPath()));
            this.combinedTypeSolver.add(new ReflectionTypeSolver(false));

            this.moshiGsonStrategy.combinedTypeSolver = combinedTypeSolver;

            LinkedList<String> gradleFilePaths = new LinkedList<>();
            getGradleFilePaths(gradleFilePaths, this.project.path);

            HashSet<String> librariesSet = new HashSet<>();
            librariesSet.addAll(libraries.keySet());
            HashMap<String, String> gradleScanResult = new HashMap<>();
            for (String gradleFilePath : gradleFilePaths) {
                GradleParser gradleParser = null;
                try {
                    gradleParser = new GradleParser(gradleFilePath, librariesSet);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                gradleScanResult.putAll(gradleParser.parse());
            }

            System.out.println("Found libraries: " + gradleScanResult.toString());

            SymbolSolverCollectionStrategy symbolSolverCollectionStrategy = new SymbolSolverCollectionStrategy();
            projectRoot = symbolSolverCollectionStrategy.collect(projectFolder.toPath());
            addLibrariesToSymbolSolver(symbolSolverCollectionStrategy, gradleScanResult);
        } else {
            projectRoot = new ParserCollectionStrategy().collect(projectFolder.toPath());
        }

        for (SourceRoot sourceRoot : projectRoot.getSourceRoots()) {
            try {
                sourceRoot.tryToParse();
                //this.project.compilationUnits.addAll(sourceRoot.getCompilationUnits());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        DeclarationLocator.compilationUnits = this.project.compilationUnits;

        List<CompilationUnit> compilationUnits = null;
        for (SourceRoot sourceRoot : projectRoot.getSourceRoots()) {
            try {
                sourceRoot.tryToParse();
                compilationUnits = sourceRoot.getCompilationUnits();
                this.project.compilationUnits.clear();
                this.project.compilationUnits.addAll(compilationUnits);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            if (!compilationUnits.isEmpty()) {
                for (CompilationUnit compilationUnit : compilationUnits) {
                    String path = compilationUnit.getStorage().map(CompilationUnit.Storage::getPath)
                            .map(Path::toString).orElse("");
                    String name = compilationUnit.getStorage().map(CompilationUnit.Storage::getPath)
                            .map(Path::getFileName).map(Path::toString).orElse("");

                    if (path == "" || name == "") {
                        continue;
                    }

                    /*
                    if (shouldPrintAST) {
                        if (name.equals("MainActivity.java")) {
                            DotPrinter printer = new DotPrinter(true);
                            try (FileWriter fileWriter = new FileWriter("/Volumes/MTDocs/DOT/" +name + ".dot");
                                PrintWriter printWriter = new PrintWriter(fileWriter)) {
                                printWriter.print(printer.output(compilationUnit));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            //System.out.println(name);
                        }
                    }
                    */


                    LinkedList<String> foundLibraries = analyzeImports(compilationUnit);

                    int snippetNbr = project.snippets.size();

                    if (enableSymbolSolving) {
                        analyzeNodeSS(compilationUnit, path, name);
                    } else {
                        analyzeNodeNSS(compilationUnit, path, name);
                    }

                    if (snippetNbr == project.snippets.size() && !foundLibraries.isEmpty()) {
                        // Add a snippet with with all of the code in the file to later manually inspect what was missed
                        for (String library : foundLibraries) {
                            project.addSnippet(new Snippet(path, name, compilationUnit.toString(), null, null, library, 0, 0, null));
                        }
                    }
                }
            }
        }

        projects.add(this.project);
    }

    private void addLibrariesToSymbolSolver(SymbolSolverCollectionStrategy symbolSolverCollectionStrategy, HashMap<String, String> librariesToInclude) {
        for (Map.Entry<String, String> entry : librariesToInclude.entrySet()) {
            File libraryFolder = new File(this.libraryFolderPath + "/" + entry.getKey() + "-" + entry.getValue());
            this.project.jsonLibraries.add(entry.getKey());
            if (libraryFolder.exists()) {
                symbolSolverCollectionStrategy.collect(libraryFolder.toPath());
                System.out.println("Added library: " + libraryFolder.getPath());
            } else {
                // Check if another version of that library exists and add that one
                File librariesRootFolder = new File(this.libraryFolderPath);
                File[] libraryFolders = librariesRootFolder.listFiles();

                if (libraryFolders == null) {
                    return;
                }

                Arrays.sort(libraryFolders, Collections.reverseOrder());
                for (File file : libraryFolders) {
                    if (file.getName().contains(entry.getKey())) {
                        symbolSolverCollectionStrategy.collect(file.toPath());
                        System.out.println("Added library: " + file.getPath());
                        break;
                    }
                }
            }
        }
    }

    private void getGradleFilePaths(LinkedList<String> gradleFilePaths, String folderPath) {
        File folder = new File(folderPath);

        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                getGradleFilePaths(gradleFilePaths, file.getPath());
            } else if (file.isFile() && file.getName().equals("build.gradle")) {
                gradleFilePaths.add(file.getPath());
            }
        }
    }

    private LinkedList<String> analyzeImports(CompilationUnit compilationUnit) {
        LinkedList<String> foundLibraries = new LinkedList<String>();

        if (compilationUnit.getImports() != null) {
            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                for (String library : this.libraries.keySet()) {
                    if (importDeclaration.getName().toString().contains(library)) {
                        foundLibraries.add(importDeclaration.getName().toString());
                    }
                }
            }
        }

        return foundLibraries;
    }

    // Analyze node with symbol solving disabled
    private void analyzeNodeNSS(Node node, String path, String name) {
        if (node instanceof MethodCallExpr || node instanceof ObjectCreationExpr || node instanceof CastExpr) {
            String foundLibrary = checkStringForNetworkingCode(node.toString());

            if (foundLibrary != null) {
                Node classOrMethodNode = getParentClassOrMethod(node);
                if (classOrMethodNode != null) {
                    if (classOrMethodNode instanceof MethodDeclaration || classOrMethodNode instanceof
                            ConstructorDeclaration) {
                        Node classNode = getParentClassOrMethod(classOrMethodNode);
                        if (classNode != null) {
                            Snippet snippet = new Snippet(path, name, classNode.toString(), classOrMethodNode.toString(), node.toString(), foundLibrary, node.getBegin().get().line, node.getEnd().get().line, null);
                            project.addSnippet(snippet);
                        } else {
                            Snippet snippet = new Snippet(path, name, null, classOrMethodNode.toString(), node.toString(), foundLibrary, node.getBegin().get().line, node.getEnd().get().line, null);
                            project.addSnippet(snippet);
                        }
                    } else if (classOrMethodNode instanceof ClassOrInterfaceDeclaration) {
                        Snippet snippet = new Snippet(path, name, classOrMethodNode.toString(), null, node.toString(), foundLibrary, node.getBegin().get().line, node.getEnd().get().line, null);
                        project.addSnippet(snippet);
                    }
                } else {
                    Snippet snippet = new Snippet(path, name, null, null, node.toString(), foundLibrary, node.getBegin().get().line, node.getEnd().get().line, null);
                    project.addSnippet(snippet);
                }
            }
        }

        for (Node child : node.getChildNodes()) {
            analyzeNodeNSS(child, path, name);
        }
    }

    // Analyze node with symbol solving enabled
    private void analyzeNodeSS(Node node, String path, String name) {
        try {
            if (node instanceof  ObjectCreationExpr) {
                analyzeObjectCreationExpr(node, path, name);
            } else if (node instanceof MethodCallExpr) {
                analyzeMethodCallExpr(node, path, name);
            } else if (node instanceof CastExpr) {
                analyzeCastExpr(node, path, name);
            } else if (node instanceof StringLiteralExpr) {
                analyzeStringLiteralExpr(node, path);
            } else if (node instanceof BinaryExpr) {
                analyzeBinaryExpr(node, path);
            } else if (node instanceof VariableDeclarator) {
                analyzeVariableDeclarator(node);
            }
        } catch (StackOverflowError e) {
            System.out.println("Error while analyzing node: " + node + ", exception: " + e);
        }

        for (Node child : node.getChildNodes()) {
            analyzeNodeSS(child, path, name);
        }
    }

    private void analyzeVariableDeclarator(Node node) {
        VariableCollector.collect((VariableDeclarator) node, project);

        boolean foundAPIURL = okHttpStrategy.extract((VariableDeclarator) node);

        if (foundAPIURL) {
            return;
        }

        List<String> assembledStringsSV = StringValueExtraction.extract((VariableDeclarator) node, this.project);
        if (assembledStringsSV != null) {
            String path = Utils.getPathForNode(node);

            boolean isValidURL = apiurlStrategy.extract(assembledStringsSV, this.project, "noLib.StringBuilder", path);

            /*
            if (isValidURL) {
                return;
            }
            */

            boolean isValidJSON = jsonStringStrategy.extract(assembledStringsSV, this.project, "noLib.StringBuilder", path);

            /*
            if (isValidJSON) {
                return;
            }
            */
        }

        List<String> assembledJSONStrings = orgjsonStrategy.extract((VariableDeclarator) node, this.project, 0);
        if (assembledJSONStrings != null) {
            String path = Utils.getPathForNode(node);

            boolean isValidJSON = jsonStringStrategy.extract(assembledJSONStrings, this.project, "org.json", path);

            if (isValidJSON) {
                return;
            }
        }
    }

    private void analyzeStringLiteralExpr(Node node, String path) {
        path = Utils.getPathForNode(node);

        // Check if StringLiteralExpr is a valid API URL
        boolean foundAPIURL = apiurlStrategy.extract(((StringLiteralExpr) node).getValue(), this.project, null,
                "noLib.StringLiteralExpr", path);
        if (foundAPIURL) {
            return;
        }

        // Check if StringLiteralExpr is a valid JSON model
        boolean foundJSONModel = jsonStringStrategy.parse((StringLiteralExpr) node, path, jsonModels);
    }

    private void analyzeBinaryExpr(Node node, String path) {
        List<String> assembledStrings = StringValueExtraction.extract((BinaryExpr) node, this.project);

        if (assembledStrings == null) {
            return;
        }

        path = Utils.getPathForNode(node);

        boolean isValidURL = apiurlStrategy.extract(assembledStrings, this.project, "noLib.StringLiteralExpr", path);

        /*
        if (isValidURL) {
            return;
        }
        */

        boolean isValidJSON = jsonStringStrategy.extract(assembledStrings, this.project, "noLib.BinaryExpr", path);
    }

    private void analyzeObjectCreationExpr(Node node, String path, String name) {
        String typeString = null;
        switch(((ObjectCreationExpr) node).getType().getName().asString()) {
            case "JsonObjectRequest": case "JsonArrayRequest": case "StringRequest": case "ImageRequest": // com.android.volley, com.mcxiaoke.volley
                //typeString = estimateType((Expression) node);
                if (((ObjectCreationExpr) node).getArguments().size() > 2) {
                    LinkedList<String> argTypes = new LinkedList<>();
                    for (Expression expression : ((ObjectCreationExpr) node).getArguments()) {
                            /*
                            String expressionType = estimateType(expression);
                            if (expressionType != null) {
                                System.out.println("Argument type: " + expressionType);
                                // argTypes.add(expressionType);
                                // Expressions like e.g. new Response.Listener<String>() { ... } can not be resolved here,
                                // have to contact JavaParser maintainers
                                // Instead of Response.Listener and response error, lambda expressions can be used...
                            }
                            */
                        if (expression instanceof ObjectCreationExpr) {
                            if (((ObjectCreationExpr) expression).getType().asString().contains("Response.Listener")) {
                                System.out.println("Type arguments: " +
                                        ((ObjectCreationExpr) expression).getType().getTypeArguments().get());
                                argTypes.add("Response.Listener");
                            }
                        }
                    }
                    if (argTypes.contains("Response.Listener")) {
                        saveSnippet(path, name, "com.android.volley",
                                ((ObjectCreationExpr) node).getType().getName().asString(), node);
                        System.out.println("Snippet saved!");
                    }
                }
                break;
            case "Builder":
                typeString = TypeEstimator.estimateTypeName((Expression) node);
                if (typeString != null) {
                    if (typeString.equals("okhttp3.Request.Builder")) {
                        saveSnippet(path, name, "com.squareup.okhttp3", typeString, node);
                    } else if (typeString.equals("com.squareup.okhttp.Request.Builder")) {
                        saveSnippet(path, name, "com.squareup.okhttp", typeString, node);
                    } else if (typeString.equals("retrofit.Retrofit.Builder")
                            || typeString.equals("retrofit2.Retrofit.Builder")
                            || typeString.equals("retrofit.RestAdapter.Builder")
                            || typeString.equals("retrofit2.RestAdapter.Builder")) {
                        saveSnippet(path, name, "com.squareup.retrofit", typeString, node);
                    }
                }
                break;
            case "Socket": // Library: java.net.Socket
                typeString = TypeEstimator.estimateTypeName((Expression) node);
                if (typeString.equals("java.net.Socket")) {
                    saveSnippet(path, name, typeString, typeString, node);
                }
                break;
            case "SSLSocket": // Library: javax.net.ssl.SSLSocket
                typeString = TypeEstimator.estimateTypeName((Expression) node);
                if (typeString.equals("javax.net.ssl.SSLSocket")) {
                    saveSnippet(path, name, typeString, typeString, node);
                }
                break;
            case "URL":
                apiurlStrategy.extract((ObjectCreationExpr) node, this.project);
                break;
            default:
                //System.out.println("Not identified: " + node.toString());
                //System.out.println("    type: " + ((ObjectCreationExpr) node).getType().getName().asString());
        }
    }

    private void analyzeMethodCallExpr(Node node, String path, String name) {
        String typeString = null;
        switch(((MethodCallExpr) node).getName().asString()) {
            case "openConnection": case "openStream": // Libraries: java.net.HttpURLConnection, java.net.URLConnection
                typeString = TypeEstimator.estimateTypeName((Expression) node);
                if (typeString != null) {
                    if (typeString.equals("java.net.URLConnection")) {
                        saveSnippet(path, name, "java.net.URLConnection", typeString, node);
                    } else if (typeString.equals("java.net.HttpURLConnection")) {
                        saveSnippet(path, name, "java.net.HttpURLConnection", typeString, node);
                    }
                }
                break;
            case "execute": // Libraries: org.apache.httpcomponents, android.net.http
                if (((MethodCallExpr) node).getScope().isPresent()) {
                    String scopeType = TypeEstimator.estimateTypeName(((MethodCallExpr) node).getScope().get());
                    if (scopeType != null) {
                        if (scopeType.equals("org.apache.http.client.HttpClient")
                                || scopeType.equals("org.apache.http.impl.client.CloseableHttpClient ")
                                || scopeType.equals("org.apache.http.impl.client.DefaultHttpClient")) {
                            saveSnippet(path, name, "org.apache.httpcomponents", scopeType, node);
                        } else if (scopeType.equals("android.net.http.AndroidHttpClient")) {
                            saveSnippet(path, name, "android.core", scopeType, node);
                        }
                    }
                }
                break;
            case "get": case "post": // Libraries: com.loopj.android
                // This does not work for com.loopj.android, even though the parameters are resolved successfully
                //typeString = estimateType((Expression) node);
                if (((MethodCallExpr) node).getArguments().size() >= 2) {
                    LinkedList<String> argTypes = new LinkedList<>();
                    for (Expression expression : ((MethodCallExpr) node).getArguments()) {
                        String expressionType = TypeEstimator.estimateTypeName(expression);
                        if (expressionType != null) {
                            argTypes.add(expressionType);
                        }
                    }
                    if (argTypes.contains("com.loopj.android.http.AsyncHttpResponseHandler")) {
                        saveSnippet(path, name, "com.loopj.android", "com.loopj.android.http.AsyncHttpClient", node);
                    }
                }
                break;
            case "with": // com.github.bumptech.glide
                typeString = TypeEstimator.estimateTypeName((Expression) node);
                if (typeString != null && typeString.equals("com.koushikdutta.ion.builder.LoadBuilder")) {
                    saveSnippet(path, name, "com.koushikdutta.ion", typeString, node);
                } else if (((MethodCallExpr) node).getScope().isPresent()) {
                    String scopeType = TypeEstimator.estimateTypeName(((MethodCallExpr) node).getScope().get());
                    if (scopeType != null) {
                        if (scopeType.equals("com.bumptech.glide.Glide")) {
                            saveSnippet(path, name, "com.github.bumptech.glide", scopeType, node);
                        } else if (scopeType.equals("com.koushikdutta.ion.Ion")) {
                            saveSnippet(path, name, "com.koushikdutta.ion", scopeType, node);
                        } else {
                            //System.out.println("Different scope type: " + scopeType);
                        }
                    }
                }
                break;
            case "getJSONObject": case "getFile": case "getJSONArray": case "getString": // com.koushikdutta.async
                // Too few examples to test (only 3 of the F Droid projects use async but in combination with ION)
                break;
            case "put":
                //orgjsonStrategy.extract(node, path, jsonModels);
                break;
            case "addProperty": case "toJson": case "adapter":
                moshiGsonStrategy.extract(node, path, jsonModels, combinedTypeSolver);
                break;
            case "concat":
                //apiurlStrategy.extract((MethodCallExpr) node, this.project);
                List<String> assembledStrings = StringValueExtraction.extract((MethodCallExpr) node, this.project);

                if (assembledStrings == null) {
                    return;
                }

                path = Utils.getPathForNode(node);

                boolean isValidURL = apiurlStrategy.extract(assembledStrings, this.project, "noLib.ConcatString", path);

                if (isValidURL) {
                    return;
                }

                boolean isValidJSON = jsonStringStrategy.extract(assembledStrings, this.project,
                        "noLib.ConcatString", path);
                break;
            case "build":
                okHttpStrategy.extract((MethodCallExpr) node, null);
                break;
            case "create":
                retrofitStrategy.extract((MethodCallExpr) node);
                break;
        }
    }

    private void analyzeCastExpr(Node node, String path, String name) {
        switch(((CastExpr) node).getType().asString()) {
            case "HttpsURLConnection": case "HttpURLConnection": // Libraries: java.net.HttpURLConnection, javax.net.ssl.HttpsURLConnection
                if (((CastExpr) node).getExpression() instanceof MethodCallExpr) {
                    switch (((MethodCallExpr) ((CastExpr) node).getExpression()).getName().asString()) {
                        case "openConnection": case "openStream":
                            String methodCallType = TypeEstimator.estimateTypeName(((CastExpr) node).getExpression());
                            String castType = TypeEstimator.estimateTypeName((Expression) node);
                            if (methodCallType != null) {
                                if (methodCallType.equals("java.net.URLConnection")) {
                                    saveSnippet(path, name, castType, castType, node);
                                }
                            }
                    }
                }
        }
    }

    private void saveSnippet(String path, String name, String library, String type, Node node) {
        Node classOrMethodNode = getParentClassOrMethod(node);
        if (classOrMethodNode != null) {
            if (classOrMethodNode instanceof MethodDeclaration || classOrMethodNode instanceof
                    ConstructorDeclaration) {
                Node classNode = getParentClassOrMethod(classOrMethodNode);
                if (classNode != null) {
                    Snippet snippet = new Snippet(path, name, classNode.toString(), classOrMethodNode.toString(), node.toString(), library, node.getBegin().get().line, node.getEnd().get().line, type);
                    project.addSnippet(snippet);
                } else {
                    Snippet snippet = new Snippet(path, name, null, classOrMethodNode.toString(), node.toString(), library, node.getBegin().get().line, node.getEnd().get().line, type);
                    project.addSnippet(snippet);
                }
            } else if (classOrMethodNode instanceof ClassOrInterfaceDeclaration) {
                Snippet snippet = new Snippet(path, name, classOrMethodNode.toString(), null, node.toString(), library, node.getBegin().get().line, node.getEnd().get().line, type);
                project.addSnippet(snippet);
            }
        } else {
            Snippet snippet = new Snippet(path, name, null, null, node.toString(), library, node.getBegin().get().line, node.getEnd().get().line, type);
            project.addSnippet(snippet);
        }
    }

    private String checkStringForNetworkingCode(String toCheck) {
        for (Map.Entry<String, HashSet<String>> entry : libraries.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (toCheck.toLowerCase().contains(keyword.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private Node getParentClassOrMethod(Node node) {
        Node retNode = null;
        try {
            retNode = node.getParentNode().get();
        } catch (NoSuchElementException e) {
            return null;
        }

        if (retNode instanceof MethodDeclaration || retNode instanceof ClassOrInterfaceDeclaration
                || retNode instanceof ConstructorDeclaration) {
            return retNode;
        } else {
            retNode = getParentClassOrMethod(retNode);
        }

        return retNode;
    }

    public void run() {
        analyze();

        System.out.println(jsonModels.size() + " detected JSON models:");
        for (Map.Entry<String, JSONRoot> jsonRootEntry : this.jsonModels.entrySet()) {
            //System.out.println("ID: " + jsonRootEntry.getKey() + "\n" + jsonRootEntry.getValue().toString());
            System.out.println("ID: " + jsonRootEntry.getKey() + "\n" + jsonRootEntry.getValue().formatJSON());
            System.out.println("Detail info: " + jsonRootEntry.getValue().getJSONDetails());
        }

        System.out.println(project.apiURLs.size() + " detected base API URLs:");
        for (Map.Entry<String, APIURL> entry : project.apiURLs.entrySet()) {
            System.out.println(entry.getValue());
        }

        JavaParserFacade.clearInstances();
        System.out.println("Processed: " + projects.size() + " of " + totalProjects);
    }
}
