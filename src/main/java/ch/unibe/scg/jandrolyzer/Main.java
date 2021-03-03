//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 26.09.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package ch.unibe.scg.jandrolyzer;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import ch.unibe.scg.jandrolyzer.Models.*;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import ch.unibe.scg.jandrolyzer.APIAnalysis.APIAnalyzer;

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

    // Analyze APIs
    @Parameter(names = {"--analyzed_project_path", "-app"}, description
            = "Analyze endpoints of an already analyzed project", variableArity = true)
    private static List<String> analyzedProjectPath = new ArrayList<>();

    // Additional arguments
    @Parameter(names = {"--libraries_path", "-lp"}, description = "Location of libraries", required = true)
    private static List<String> librariesPath = new ArrayList<>();

    @Parameter(names = {"-httpRequests", "-http"}, description = "Make HTTP requests to collected endpoints")
    private static boolean httpRequests = false;

    @Parameter(names = {"-decompilation_only", "-do"}, description = "Run decompilation only")
    private static boolean decompilationOnly = false;

    @Parameter(names = {"-recursion_depth", "-rd"}, description = "Set max allowed recursion depth")
    public static int maxRecursionDepth = -1;

    @Parameter(names = {"-output_path_json", "-oj"}, description = "Path for JSON results", variableArity = true)
    private static List<String> outputPathJSON = new ArrayList<>();

    @Parameter(names = {"-output_path_success", "-os"}, description = "Path for success state", variableArity = true)
    private static List<String> outputPathSuccess = new ArrayList<>();


    // HashSet not ordered according to insertion order
    static HashMap<String, HashSet<String>> libraries;

    public static void main(String args[]) {
        libraries = Utils.getLibraries();

        Main main = new Main();
        JCommander.newBuilder().addObject(main).build().parse(args);

        if (!projectPath.isEmpty() && !outputPathJSON.isEmpty()) {
            analyzeSingleProject(argToPath(projectPath), argToPath(librariesPath), argToPath(outputPathJSON));
        } else if (!projectsPath.isEmpty()) {
            // the method below should become deprecated (no distinct output folders supported)
            analyzeMultipleProjects(argToPath(projectsPath), argToPath(librariesPath));
        } else if (!apkPath.isEmpty()) {
            if (jadxPath.isEmpty() || outputPath.isEmpty() || outputPathSuccess.isEmpty()) {
                throw new IllegalArgumentException("Please specify JADX path, output path, output path for JSON results, and output path for success state.");
            }
            analyzeSingleAPK(argToPath(apkPath), argToPath(jadxPath), argToPath(outputPath), argToPath(librariesPath), argToPath(outputPathJSON), argToPath(outputPathSuccess));
        } else if (!apksPath.isEmpty()) {
            if (jadxPath.isEmpty() || outputPath.isEmpty() || outputPathSuccess.isEmpty()) {
                throw new IllegalArgumentException("Please specify JADX path, output path, output path for JSON results, and output path for success state.");
            }

            analyzeMultipleAPKs(argToPath(apksPath), argToPath(jadxPath), argToPath(outputPath), argToPath(librariesPath), argToPath(outputPathJSON), argToPath(outputPathSuccess));
        } else if (!analyzedProjectPath.isEmpty() && !outputPathJSON.isEmpty()) {
            analyzeAPIs(null, argToPath(analyzedProjectPath), argToPath(outputPathJSON));
        } else {
            System.out.println("Warning: Invalid parameter configuration found. Aborting.");
        }

        System.out.println("All done!");
    }

    private static String argToPath(List<String> arg) {
        StringBuilder stringBuilder = new StringBuilder();

        if (arg.isEmpty()) {
            return "";
        }

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

    static void analyzeMultipleAPKs(String pathToAPKsFolder, String pathToJadx, String outputPath, String librariesPath, String jsonPath, String successPath) {
        Decompiler decompiler = new Decompiler(null, pathToAPKsFolder, pathToJadx, outputPath, successPath);
        ArrayList<String> projectPathsList = decompiler.startDecompilation();

        if (!projectPathsList.isEmpty()) {
            for (String path : projectPathsList) {
                if (!decompilationOnly) {
                    analyzeSingleProject(path, librariesPath, jsonPath);
                }
            }
        }
    }

    // outputPath specifies the location of the decompiled Android project
    static void analyzeSingleAPK(String pathToAPK, String pathToJadx, String outputPath, String librariesPath, String jsonPath, String successPath) {
        Decompiler decompiler = new Decompiler(pathToAPK, null, pathToJadx, outputPath, successPath);
        ArrayList<String> projectPathList = decompiler.startDecompilation();

        if (!projectPathList.isEmpty()) {
            if (!decompilationOnly) {
                analyzeSingleProject(projectPathList.get(0), librariesPath, jsonPath);
            }
        } else {
            System.out.println("ProjectPathList is empty");
        }
    }

    static void analyzeSingleProject(String projectPath, String librariesPath, String jsonPath) {
        List<Project> projects = new LinkedList<>();

        try {
            ProjectAnalyzer projectAnalyzer = new ProjectAnalyzer(projectPath, libraries, projects, 1, librariesPath);
            projectAnalyzer.run();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println(projects.get(0).minimalStringRepresentation());

        analyzeAPIs(projects, null, jsonPath);
    }

    static void analyzeAPIs(List<Project> projects, String projectPath, String jsonPath) {
        APIAnalyzer apiAnalyzer = new APIAnalyzer(projects, httpRequests, projectPath, jsonPath);
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
