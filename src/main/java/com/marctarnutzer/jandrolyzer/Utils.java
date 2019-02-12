//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 18.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.NoSuchElementException;

public class Utils {

    static HashMap<String, HashSet<String>> libraries = new HashMap<>();

    public static String getPathForNode(Node node) {
        String path = null;
        if (node.findCompilationUnit().isPresent()) {
            path = node.findCompilationUnit().get().getStorage().map(CompilationUnit.Storage::getPath)
                    .map(Path::toString).orElse(null);
        }

        return path;
    }

    public static HashMap<String, HashSet<String>> getLibraries() {
        if (libraries.isEmpty()) {
            initLibraries();
        }

        return libraries;
    }

    public static Node getParentClassOrMethod(Node node) {
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

    public static String removeEscapeSequencesFrom(String inputString) {
        inputString = inputString.replace("\\\"", "\"");
        inputString = inputString.replace("\\'", "'");
        inputString = inputString.replace("\\\\", "\\");

        return inputString;
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

        HashSet<String> gson = new HashSet<String>();
        libraries.put("com.google.code.gson", gson);

        HashSet<String> moshi = new HashSet<String>();
        libraries.put("com.squareup.moshi", moshi);
    }

}
