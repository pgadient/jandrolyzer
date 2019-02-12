//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 28.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.APIAnalysis;

import com.marctarnutzer.jandrolyzer.Models.*;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class APIAnalyzer {

    private List<Project> projects;
    private String projectPath;
    private boolean shouldMakeHttpRequests;
    private Set<String> httpMethods = new HashSet<>(Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE"));
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    public ConcurrentLinkedQueue<RequestResponse> requestResponses = new ConcurrentLinkedQueue<>();

    public APIAnalyzer(List<Project> projects, boolean shouldMakeHttpRequests, String projectPath) {
        this.projects = projects;
        this.shouldMakeHttpRequests = shouldMakeHttpRequests;
        this.projectPath = projectPath;
    }

    public void analyzeAll() {
        if (projects != null) {
            for (Project project : projects) {
                System.out.println("Analyzing API endpoints of project: " + project.name);
                System.out.println("Preparing data...");
                try {
                    prepareData(project);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (shouldMakeHttpRequests) {
                    System.out.println("Testing endpoints...");
                    testEndpoints(project.path);
                }
            }
        } else if (projectPath != null) {
            System.out.println("Testing endpoints...");
            testEndpoints(projectPath);
        }
    }

    /*
     * Fills found URL queries and JSON structures with values and saves results to file
     * for later processing.
     */
    private void prepareData(Project project) throws IOException {
        Path path = Paths.get(project.path, "extractedData.jan");
        BufferedWriter writer = new BufferedWriter(new FileWriter(path.toString()));
        writer.write("ENDPOINTS:");
        writer.newLine();


        List<String> nonPopulatedURLs = new LinkedList<>();
        List<String> populatedURLs = new LinkedList<>();
        for (APIURL apiurl : project.apiURLs.values()) {
            String baseURL = apiurl.getBaseURL();
            for (APIEndpoint apiEndpoint : apiurl.endpoints.values()) {
                HttpUrl.Builder urlBuilder = HttpUrl.parse(baseURL).newBuilder();
                urlBuilder.addEncodedPathSegments(apiEndpoint.path);
                for (Map.Entry<String, String> query : apiEndpoint.queries.entrySet()) {
                    urlBuilder.addEncodedQueryParameter(query.getKey(), query.getValue());
                }
                for (String fragment : apiEndpoint.fragments) {
                    urlBuilder.encodedFragment(fragment);
                }

                HttpUrl httpUrl = urlBuilder.build();
                nonPopulatedURLs.add(httpUrl.toString());
            }

            SampleGenerator.populateQueryValues(apiurl, project);

            for (APIEndpoint apiEndpoint : apiurl.endpoints.values()) {
                HttpUrl.Builder urlBuilder = HttpUrl.parse(baseURL).newBuilder();
                String urlPath = SampleGenerator.populateURLPart(apiEndpoint.path);
                urlBuilder.addEncodedPathSegments(urlPath);
                for (Map.Entry<String, String> query : apiEndpoint.queries.entrySet()) {
                    String queryKey = SampleGenerator.populateURLPart(query.getKey());
                    String queryValue = SampleGenerator.populateURLPart(query.getValue());
                    urlBuilder.addEncodedQueryParameter(queryKey, queryValue);
                }
                for (String fragment : apiEndpoint.fragments) {
                    String f = SampleGenerator.populateURLPart(fragment);
                    urlBuilder.encodedFragment(f);
                }

                HttpUrl httpUrl = urlBuilder.build();
                populatedURLs.add(httpUrl.toString());
            }
        }

        for (int i = 0; i < nonPopulatedURLs.size(); i++) {
            writer.write(nonPopulatedURLs.get(i));
            writer.newLine();
            writer.write(populatedURLs.get(i));
            writer.newLine();
        }

        writer.write("JSON:");
        writer.newLine();

        for (JSONRoot jsonRoot : project.jsonModels.values()) {
            String nonPopulatedJSONString = jsonRoot.formatJSONWithoutValues();
            String populatedJSONString = SampleGenerator.populateJSON(jsonRoot, project);

            writer.write(nonPopulatedJSONString);
            writer.newLine();
            writer.write(populatedJSONString);
            writer.newLine();
        }

        writer.write("STRING VARIABLES:");
        writer.newLine();

        for (Map.Entry<String, Set<String>> entry : project.stringVariables.entrySet()) {
            JSONArray jsonArray = new JSONArray();
            for (String value : entry.getValue()) {
                jsonArray.put(value);
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(entry.getKey(), jsonArray);

            writer.write(jsonObject.toString());
            writer.newLine();
        }

        writer.write("JSON DETAILS:");
        writer.newLine();

        for (JSONRoot jsonRoot : project.jsonModels.values()) {
            writer.write(jsonRoot.getJSONDetails());
        }

        writer.write("URL DETAILS:");
        writer.newLine();

        for (APIURL apiurl : project.apiURLs.values()) {
            writer.write(apiurl.toString());
        }

        writer.write("SNIPPETS:");
        writer.newLine();

        for (Snippet snippet : project.snippets) {
            writer.write(snippet.toString());
        }

        writer.close();

        System.out.println("Saved data");
    }

    private void testEndpoints(String projectPath) {
        List<String> endpoints = new LinkedList<>();
        List<String> jsonStrings = new LinkedList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(Paths.get(projectPath, "extractedData.jan").toString()));
            String line = reader.readLine();
            line = reader.readLine();
            int i = 0;
            while (!line.equals("JSON:")) {
                i++;
                if ((i % 2) == 0) {
                    endpoints.add(line);
                }
                line = reader.readLine();
            }
            line = reader.readLine();
            i = 0;
            while (!line.equals("STRING VARIABLES:")) {
                i++;
                if ((i % 2) == 0) {
                    jsonStrings.add(line);
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int requestsCount = endpoints.size() * httpMethods.size() + (endpoints.size() * (httpMethods.size() - 1) * jsonStrings.size());
        CountDownLatch latch = new CountDownLatch(requestsCount);

        for (String endpoint : endpoints) {
            HttpUrl httpUrl = HttpUrl.parse(endpoint);
            for (String httpMethod : httpMethods) {
                if (httpMethod.equals("GET")) {
                    System.out.println("Testing URL: " + endpoint + " [" + httpMethod + "]");
                    makeRequest(httpUrl, null, httpMethod, requestResponses, latch);
                } else if (httpMethod.equals("POST")) {
                    System.out.println("Testing URL: " + endpoint + " [" + httpMethod + "]");
                    makeRequest(httpUrl, null, httpMethod, requestResponses, latch);

                    for (String jsonString : jsonStrings) {
                        System.out.println("Making request with JSON: " + jsonString);
                        makeRequest(httpUrl, jsonString, httpMethod, requestResponses, latch);
                    }
                } else {
                    System.out.println("Blocked testing of URL: " + endpoint + " [" + httpMethod + "], latch: " + latch.getCount());
                    latch.countDown();
                    for (String jsonSring : jsonStrings) {
                        latch.countDown();
                    }
                }
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("API Endpoint results: ");
        for (RequestResponse requestResponse : requestResponses) {
            System.out.println("Request response: \n" + requestResponse);
        }

        Path path = Paths.get(projectPath, "RequestResponses.jan");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toString()))) {
            for (RequestResponse requestResponse : requestResponses) {
                writer.write(requestResponse.toString());
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void makeRequest(HttpUrl httpUrl, String jsonString, String httpMethod,
                             ConcurrentLinkedQueue<RequestResponse> requestResponses, CountDownLatch latch) {
        Request request = createRequest(httpUrl, jsonString, httpMethod);

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requestResponses.add(new RequestResponse(httpUrl.toString(),
                        httpMethod, false, e.toString(), null, jsonString, -1));

                latch.countDown();
                System.out.println("Latch: " + latch.getCount() + "API call failed: " + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        System.out.println("API call not successful, response: " + response);
                    } else {
                        System.out.println("API call successful, response: " + response);
                    }

                    System.out.println("Response message: " + response.message());
                    System.out.println("Response is redirect: " + response.isRedirect());
                    System.out.println("Response is protocol: " + response.protocol());

                    Headers headers = response.headers();
                    Map<String, String> headerMap = new HashMap<>();
                    for (int i = 0, size = headers.size(); i < size; i++) {
                        headerMap.put(headers.name(i), headers.value(i));
                    }

                    String respBody = responseBody.string();
                    RequestResponse requestResponse = new RequestResponse(httpUrl.toString(), httpMethod,
                            response.isSuccessful(), response.message(), respBody, jsonString, response.code());
                    requestResponse.headers = headerMap;
                    requestResponses.add(requestResponse);
                } finally {
                    latch.countDown();
                    System.out.println("Request success, latch: "+ latch.getCount());
                }
            }
        });
    }

    private Request createRequest(HttpUrl httpUrl, String jsonString, String httpMethod) {
        Request request = null;

        if (httpMethod.equals("GET")) {
            request = new Request.Builder()
                    .url(httpUrl)
                    .build();
        } else {
            RequestBody requestBody = createRequestBody(jsonString);

            if (httpMethod.equals("PUT")) {
                request = new Request.Builder()
                        .url(httpUrl)
                        .put(requestBody)
                        .build();
            } else if (httpMethod.equals("POST")) {
                request = new Request.Builder()
                        .url(httpUrl)
                        .post(requestBody)
                        .build();
            } else if (httpMethod.equals("PATCH")) {
                request = new Request.Builder()
                        .url(httpUrl)
                        .patch(requestBody)
                        .build();
            } else if (httpMethod.equals("DELETE")) {
                request = new Request.Builder()
                        .url(httpUrl)
                        .delete(requestBody)
                        .build();
            }
        }

        return request;
    }

    private RequestBody createRequestBody(String jsonString) {
        RequestBody requestBody;
        MediaType jsonMediaType;
        if (jsonString == null) {
            requestBody = RequestBody.create(null, new byte[0]);
        } else {
            jsonMediaType = MediaType.parse("application/json; charset=utf-8");
            requestBody = RequestBody.create(jsonMediaType, jsonString);
        }

        return requestBody;
    }

}
