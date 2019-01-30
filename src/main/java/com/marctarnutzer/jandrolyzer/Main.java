//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 26.09.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.marctarnutzer.jandrolyzer.APIAnalysis.APIAnalyzer;
import com.marctarnutzer.jandrolyzer.Models.*;

public class Main {
    // Scan APK file(s)
    @Parameter(names = {"--apk_path", "-ap"}, description = "Path to single APK file", variableArity = true)
    private static List<String> apkPath = new ArrayList<>();

    @Parameter(names = {"--apks_path", "-asp"}, description = "Path to folder with multiple APK files",
            variableArity = true)
    private static List<String> apksPath = new ArrayList<>();

    @Parameter(names = {"--jadx_path", "-jp"}, description = "Path to JADX binary", variableArity = true)
    private static List<String> jadxPath = new ArrayList<>();

    @Parameter(names = {"--output_path", "-op"}, description = "Decompiled project output path",
            variableArity = true)
    private static List<String> outputPath = new ArrayList<>();

    // Scan open source project(s)
    @Parameter(names = {"--project_path", "-pp"}, description = "Scan a single projects folder", variableArity = true)
    private static List<String> projectPath = new ArrayList<>();

    @Parameter(names = {"--projects_path", "-psp"}, description = "Scan a folder containing multiple projects",
            variableArity = true)
    private static List<String> projectsPath = new ArrayList<>();

    // Additional arguments
    @Parameter(names = {"--libraries_path", "-lp"}, description = "Location of libraries", required = true)
    private static List<String> librariesPath = new ArrayList<>();

    @Parameter(names = {"-httpRequests", "-http"}, description = "Make HTTP requests to collected to endpoints")
    private static boolean httpRequests = false;

    // HashSet not ordered according to insertion order
    static HashMap<String, HashSet<String>> libraries;

    public static void main(String args[]) {
        libraries = Utils.getLibraries();

        Main main = new Main();
        JCommander.newBuilder().addObject(main).build().parse(args);

        if (!projectPath.isEmpty()) {
            analyzeSingleProject(argToPath(projectPath), argToPath(librariesPath));
        } else if (!projectsPath.isEmpty()) {
            analyzeMultipleProjects(argToPath(projectsPath), argToPath(librariesPath));
        } else if (!apkPath.isEmpty()) {
            if (jadxPath.isEmpty() || outputPath.isEmpty()) {
                throw new IllegalArgumentException("Please specify JADX path and output path.");
            }

            analyzeSingleAPK(argToPath(apkPath), argToPath(jadxPath), argToPath(outputPath), argToPath(librariesPath));
        } else if (!apksPath.isEmpty()) {
            if (jadxPath.isEmpty() || outputPath.isEmpty()) {
                throw new IllegalArgumentException("Please specify JADX path and output path.");
            }

            analyzeMultipleAPKs(argToPath(apksPath), argToPath(jadxPath), argToPath(outputPath),
                    argToPath(librariesPath));
        }

        System.out.println("All done!");
    }

    private static String argToPath(List<String> arg) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String partPath : arg) {
            stringBuilder.append(partPath + "\\ ");
        }

        String path = stringBuilder.toString().substring(0, stringBuilder.toString().length() - 2);

        /*
        if (path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        */

        return path;
    }

    static void analyzeMultipleAPKs(String pathToAPKsFolder, String pathToJadx, String outputPath, String librariesPath) {
        Decompiler decompiler = new Decompiler(null, pathToAPKsFolder, pathToJadx, outputPath);
        ArrayList<String> projectPathsList = decompiler.startDecompilation();

        if (!projectPathsList.isEmpty()) {
            for (String path : projectPathsList) {
                analyzeSingleProject(path, librariesPath);
            }
        }
    }

    // outputPath specifies the location of the decompiled Android project
    static void analyzeSingleAPK(String pathToAPK, String pathToJadx, String outputPath, String librariesPath) {
        Decompiler decompiler = new Decompiler(pathToAPK, null, pathToJadx, outputPath);
        ArrayList<String> projectPathList = decompiler.startDecompilation();

        if (!projectPathList.isEmpty()) {
            analyzeSingleProject(projectPathList.get(0), librariesPath);
        }
    }

    static void analyzeSingleProject(String projectPath, String librariesPath) {
        List<Project> projects = new LinkedList<>();

        try {
            ProjectAnalyzer projectAnalyzer = new ProjectAnalyzer(projectPath, libraries, projects, 1, librariesPath);
            projectAnalyzer.run();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println(projects.get(0).minimalStringRepresentation());

        analyzeAPIs(projects);
    }

    static void analyzeAPIs(List<Project> projects) {
        if (!httpRequests) {
            return;
        }

        APIAnalyzer apiAnalyzer = new APIAnalyzer(projects);
        apiAnalyzer.analyzeAll();
    }

    static void analyzeMultipleProjects(String projectsPath, String librariesPath) {
        File folder = new File(projectsPath);
        File[] projectFolders = folder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });

        List<Project> projects = new LinkedList<>();

        for (File projectFolder : projectFolders) {
            try {
                ProjectAnalyzer projectAnalyzer = new ProjectAnalyzer(projectFolder.getPath(), libraries, projects, projectFolders.length, librariesPath);
                projectAnalyzer.run();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        /*
        for (Project project : projects) {
            System.out.println("Project: " + project.minimalStringRepresentation());
        }
        */

        saveResults(projectsPath, projects);

        printStatistics(projects);
    }

    static void saveResults(String path, List<Project> projects) {
        Map<String, BufferedWriter> writers = new HashMap<String, BufferedWriter>();
        for (String library : libraries.keySet()) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(path + "/" + library + ".log"));
                writers.put(library, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (Project project : projects) {
            for (Snippet snippet : project.snippets) {
                try {
                    writers.get(snippet.library).write(snippet.toString());
                    writers.get(snippet.library).newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    //e.printStackTrace();
                }
            }
        }

        for (BufferedWriter writer : writers.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Save found JSON models
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(path + "/JSONModels.log"));
            for (Project project : projects) {
                for (JSONRoot jsonRoot : project.jsonModels.values()) {
                    writer.write(jsonRoot.logInfoAndJSON());
                }
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printStatistics(List<Project> projects) {
        System.out.println("Projects analyzed: " + projects.size());

        HashMap<String, Integer> librariesOccurrences = new HashMap<>();
        for (String library : libraries.keySet()) {
            librariesOccurrences.put(library, 0);
        }

        HashMap<String, Integer> jsonLibsModelsOccurrences = new HashMap<>();
        jsonLibsModelsOccurrences.put("com.google.code.gson", 0);
        jsonLibsModelsOccurrences.put("com.squareup.moshi", 0);
        jsonLibsModelsOccurrences.put("org.json", 0);
        jsonLibsModelsOccurrences.put("noLib.StringLiteralExpr", 0);
        jsonLibsModelsOccurrences.put("noLib.BinaryExpr", 0);

        int projectsWithLibraries = 0;
        int projectsWithOneLibrary = 0;
        int projectsWithExtractedJSONModels = 0;

        Map<String, Integer> fieldNameOccurrences = new HashMap<>();

        for (Project project : projects) {
            for (String libInProject : project.jsonLibraries) {
                librariesOccurrences.put(libInProject, librariesOccurrences.get(libInProject) + 1);
            }

            if (!project.jsonLibraries.isEmpty()) {
                projectsWithLibraries++;
                if (project.jsonLibraries.size() == 1) {
                    projectsWithOneLibrary++;
                }
            }

            if (!project.jsonModels.isEmpty()) {
                projectsWithExtractedJSONModels++;
            }

            for (JSONRoot jsonRoot : project.jsonModels.values()) {
                jsonLibsModelsOccurrences.put(jsonRoot.library, jsonLibsModelsOccurrences.get(jsonRoot.library) + 1);

                collectFieldOccurrences(jsonRoot.jsonObject, fieldNameOccurrences);
            }
        }

        System.out.println("Projects with libraries: " + projectsWithLibraries);
        System.out.println("Projects with one library: " + projectsWithOneLibrary);
        System.out.println("Projects with extracted JSON Models: " + projectsWithExtractedJSONModels);

        Map<String, Integer> sortedLO = librariesOccurrences.entrySet()
                .stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2, LinkedHashMap::new));

        System.out.println("Libraries usage charts:");
        for (Map.Entry<String, Integer> entry : sortedLO.entrySet()) {
            System.out.println("    Library: " + entry.getKey() + ", occurrences: " + entry.getValue());
        }

        Map<String, Integer> sortedLMO = jsonLibsModelsOccurrences.entrySet()
                .stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2, LinkedHashMap::new));

        System.out.println("Found JSON models per library usage charts:");
        for (Map.Entry<String, Integer> entry : sortedLMO.entrySet()) {
            System.out.println("    Library: " + entry.getKey() + ", models found: " + entry.getValue());
        }

        System.out.println("Field names occurrences in detected JSON models:");
        Map<String, Integer> sortedFNO = fieldNameOccurrences.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2, LinkedHashMap::new));
        for (Map.Entry<String, Integer> entry : sortedFNO.entrySet()) {
            System.out.println("    Field name: " + entry.getKey() + ", occurrences: " + entry.getValue());
        }
    }

    static void collectFieldOccurrences(JSONObject jsonObject, Map<String, Integer> fieldNameOccurrences) {
        if (jsonObject.linkedHashMap != null && !jsonObject.linkedHashMap.isEmpty()) {
            for (Map.Entry<String, JSONObject> entry: jsonObject.linkedHashMap.entrySet()) {
                if (fieldNameOccurrences.containsKey(entry.getKey())) {
                    fieldNameOccurrences.put(entry.getKey(), fieldNameOccurrences.get(entry.getKey()) + 1);
                } else {
                    fieldNameOccurrences.put(entry.getKey(), 1);
                }

                if (entry.getValue().jsonDataType == JSONDataType.ARRAY
                        || entry.getValue().jsonDataType == JSONDataType.OBJECT) {
                    collectFieldOccurrences(entry.getValue(), fieldNameOccurrences);
                }
            }
        } else if (jsonObject.arrayElementsSet != null && !jsonObject.arrayElementsSet.isEmpty()) {
            for (JSONObject jo : jsonObject.arrayElementsSet) {
                collectFieldOccurrences(jo, fieldNameOccurrences);
            }
        }
    }
}
