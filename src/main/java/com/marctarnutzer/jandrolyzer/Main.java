//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 26.09.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Main {
    // Scan APK file(s)
    @Parameter(names = {"--apk_path", "-ap"}, description = "Path to single APK file", variableArity = true)
    private static List<String> apkPath = new ArrayList<>();

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

    @Parameter(names = {"--multithreaded", "-m"}, description = "Scan multiple projects at once")
    private static boolean multithreaded = false;

    // HashSet not ordered according to insertion order
    static HashMap<String, HashSet<String>> libraries = new HashMap<String, HashSet<String>>();

    public static void main(String args[]) {
        initLibraries();

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

            analyzeSingleAPK(argToPath(apkPath), argToPath(jadxPath), argToPath(outputPath));
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

    static void initLibraries() {
        HashSet<String> retrofitCode = new HashSet<String>();
        retrofitCode.add("Retrofit");
        retrofitCode.add("Retrofit.Builder");
        retrofitCode.add("RequestBody");
        retrofitCode.add("ResponseBody");
        retrofitCode.add("@POST");
        retrofitCode.add("@GET");
        retrofitCode.add("@PUT");
        retrofitCode.add("@PATCH");
        libraries.put("com.squareup.retrofit", retrofitCode);

        HashSet<String> retrofitCode2 = new HashSet<String>();
        libraries.put("com.squareup.retrofit2", retrofitCode2);

        HashSet<String> okhttpCode1_2 = new HashSet<String>();
        libraries.put("com.squareup.okhttp", okhttpCode1_2);

        HashSet<String> okhttpCode = new HashSet<String>();
        okhttpCode.add("OkHttpClient");
        okhttpCode.add("Request.Builder");
        okhttpCode.add("newCall");
        okhttpCode.add("RequestBody.create");
        libraries.put("com.squareup.okhttp3", okhttpCode);

        HashSet<String> glideCode = new HashSet<String>();
        glideCode.add("GlideApp");
        glideCode.add("Glide");
        libraries.put("com.github.bumptech.glide", glideCode);

        HashSet<String> httpcomponentsCode = new HashSet<String>();
        httpcomponentsCode.add("HttpClients");
        httpcomponentsCode.add("HttpClient");
        httpcomponentsCode.add("HttpPost");
        httpcomponentsCode.add("HttpGet");
        httpcomponentsCode.add("HttpResponse");
        httpcomponentsCode.add("httpclient.execute");
        httpcomponentsCode.add("CloseableHttpResponse");
        httpcomponentsCode.add("DefaultHttpClient");
        libraries.put("org.apache.httpcomponents", httpcomponentsCode);

        HashSet<String> legacyApacheCode = new HashSet<String>();
        libraries.put("org.apache.http.legacy", legacyApacheCode);

        HashSet<String> httpclientCode = new HashSet<String>(); // Deprecated (Apache http library keywords)
        libraries.put("cz.msebera.android", httpclientCode);

        HashSet<String> aVolleyCode = new HashSet<String>();
        aVolleyCode.add("RequestQueue");
        aVolleyCode.add("Volley.newRequestQueue");
        aVolleyCode.add("Volley");
        aVolleyCode.add("StringRequest");
        aVolleyCode.add("Request.Method");
        aVolleyCode.add("JsonObjectRequest");
        aVolleyCode.add("JsonArrayRequest");
        aVolleyCode.add("HttpHeaderParser");
        libraries.put("com.android.volley", aVolleyCode);

        HashSet<String> mVolleyCode = new HashSet<String>(); // Deprecated library
        libraries.put("com.mcxiaoke.volley", mVolleyCode);

        HashSet<String> androidAsyncCode = new HashSet<String>();
        androidAsyncCode.add("AsyncHttpClient");
        androidAsyncCode.add("AsyncHttpResponseHandler");
        androidAsyncCode.add("JsonHttpResponseHandler");
        androidAsyncCode.add("RequestParams");
        androidAsyncCode.add("FileAsyncHttpResponseHandler");
        androidAsyncCode.add("setBasicAuth");
        androidAsyncCode.add("AuthScope");
        androidAsyncCode.add("prepareGet");
        libraries.put("com.loopj.android", androidAsyncCode);

        HashSet<String> ionCode = new HashSet<String>();
        ionCode.add("Ion.with");
        libraries.put("com.koushikdutta.ion", ionCode);

        HashSet<String> kAndroidAsyncCode = new HashSet<String>();
        kAndroidAsyncCode.add("AsyncHttpServer");
        kAndroidAsyncCode.add("AsyncServer");
        kAndroidAsyncCode.add("AsyncHttpResponse");
        kAndroidAsyncCode.add("AsyncHttpClient");
        kAndroidAsyncCode.add("WebSocketConnectCallback");
        kAndroidAsyncCode.add("ConnectCallback");
        kAndroidAsyncCode.add("SocketIOClient");
        kAndroidAsyncCode.add("AsyncHttpPost");
        kAndroidAsyncCode.add("HttpServerRequestCallback");
        libraries.put("com.koushikdutta.async", kAndroidAsyncCode);

        HashSet<String> urlConnectionCode = new HashSet<String>();
        urlConnectionCode.add("URLConnection");
        urlConnectionCode.add("openConnection()");
        libraries.put("java.net.URLConnection", urlConnectionCode);

        HashSet<String> httpURLConnectionCode = new HashSet<String>();
        httpURLConnectionCode.add("HttpURLConnection");
        httpURLConnectionCode.add("openConnection()");
        httpURLConnectionCode.add("setRequestMethod");
        libraries.put("java.net.HttpURLConnection", httpURLConnectionCode);

        HashSet<String> httpsURLConnectionCode = new HashSet<String>();
        httpsURLConnectionCode.add("HttpsURLConnection");
        httpsURLConnectionCode.add("setRequestMethod");
        httpsURLConnectionCode.add("openConnection()");
        libraries.put("javax.net.ssl.HttpsURLConnection", httpsURLConnectionCode);

        HashSet<String> socketCode = new HashSet<String>();
        socketCode.add("Socket");
        socketCode.add("SocketFactory");
        libraries.put("java.net.Socket", socketCode);

        HashSet<String> sslSocketCode = new HashSet<String>();
        sslSocketCode.add("SSLSocket");
        sslSocketCode.add("SSLSocketFactory");
        libraries.put("javax.net.ssl.SSLSocket", sslSocketCode);

        HashSet<String> androidCore = new HashSet<String>();
        androidCore.add("AndroidHttpClient");
        androidCore.add("DefaultHttpClient");
        androidCore.add("HttpGet");
        androidCore.add("HttpClient");
        libraries.put("android.core", androidCore);
    }

    // outputPath specifies the location of the decompiled Android project
    static void analyzeSingleAPK(String pathToAPK, String pathToJadx, String outputPath) {
        Decompiler decompiler = new Decompiler(pathToAPK, null, pathToJadx, outputPath);
        decompiler.startDecompilation();
    }

    static void analyzeSingleProject(String projectPath, String librariesPath) {
        ArrayBlockingQueue<Project> projects = new ArrayBlockingQueue<>(1);
        CountDownLatch latch = new CountDownLatch(1);
        Semaphore concurrentAnalyzers = new Semaphore(1);

        try {
            ProjectAnalyzer projectAnalyzer = new ProjectAnalyzer(projectPath, libraries, projects, latch, 1, concurrentAnalyzers, librariesPath);
            projectAnalyzer.run();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //System.out.println(projects.peek().minimalStringRepresentation());
    }

    static void analyzeMultipleProjects(String projectsPath, String librariesPath) {
        File folder = new File(projectsPath);
        File[] projectFolders = folder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        ArrayBlockingQueue<Project> projects = new ArrayBlockingQueue<Project>(projectFolders.length);
        CountDownLatch latch = new CountDownLatch(projectFolders.length);

        Semaphore concurrentAnalyzers;
        if (multithreaded) {
            concurrentAnalyzers = new Semaphore(Runtime.getRuntime().availableProcessors());
        } else {
            concurrentAnalyzers = new Semaphore(1);
        }

        //ProjectAnalyzer projectAnalyzer;
        for (File projectFolder : projectFolders) {
            try {
                concurrentAnalyzers.acquire();
                //Thread thread = new Thread(new ProjectAnalyzer(projectFolder.getPath(), libraries, projects, latch, projectFolders.length, concurrentAnalyzers, librariesPath));
                //thread.start();
                ProjectAnalyzer projectAnalyzer = new ProjectAnalyzer(projectFolder.getPath(), libraries, projects, latch, projectFolders.length, concurrentAnalyzers, librariesPath);
                projectAnalyzer.run();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
        for (Project project : projects) {
            System.out.println("Project: " + project.minimalStringRepresentation());
        }*/

        saveResults(projectsPath, projects);
    }

    static void saveResults(String path, ArrayBlockingQueue<Project> projects) {
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
    }
}
