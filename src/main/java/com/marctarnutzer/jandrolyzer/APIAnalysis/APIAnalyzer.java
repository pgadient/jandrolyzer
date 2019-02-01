//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 28.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.APIAnalysis;

import com.marctarnutzer.jandrolyzer.Models.*;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class APIAnalyzer {

    private List<Project> projects;
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

    public APIAnalyzer(List<Project> projects) {
        this.projects = projects;
    }

    public void analyzeAll() {
        for (Project project : projects) {
            System.out.println("Analyzing API endpoints of project: " + project.name);

            analyzeAPIEndpoints(project);
        }
    }

    private void analyzeAPIEndpoints(Project project) {
        int requestsCount = 0;
        for (APIURL apiurl : project.apiURLs.values()) {
            for (APIEndpoint apiEndpoint : apiurl.endpoints.values()) {
                requestsCount++;
            }
        }
        int httpMethodCount = 1;
        requestsCount = requestsCount * httpMethodCount;
        CountDownLatch latch = new CountDownLatch(requestsCount);

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

                for (String httpMethod : httpMethods) {
                    if (httpMethod.equals("GET")) {
                        System.out.println("Testing URL: " + httpUrl + " [" + httpMethod + "]");

                        //makeRequest(httpUrl, null, httpMethod, project.requestResponses, latch);
                    } else if (httpMethod.equals("POST")) {
                        System.out.println("Testing URL: " + httpUrl + " [" + httpMethod + "]");

                        //makeRequest(httpUrl, null, httpMethod, project.requestResponses, latch);

                        for (JSONRoot jsonRoot : project.jsonModels.values()) {
                            String jsonString = SampleGenerator.populateJSON(jsonRoot, project);

                            System.out.println("Making request with JSON: " + jsonString);

                            //makeRequest(httpUrl, jsonString, httpMethod, project.requestResponses, latch);
                        }
                    } else {
                        System.out.println("Blocked testing of URL: " + httpUrl + " [" + httpMethod + "]");
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
        for (RequestResponse requestResponse : project.requestResponses) {
            System.out.println("Request response: \n" + requestResponse);
        }
    }

    private void makeRequest(HttpUrl httpUrl, String jsonString, String httpMethod,
                             ConcurrentLinkedQueue<RequestResponse> requestResponses, CountDownLatch latch) {
        Request request = createRequest(httpUrl, jsonString, httpMethod);

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.out.println("API call failed: " + e);
                requestResponses.add(new RequestResponse(httpUrl.toString(),
                        httpMethod, false, e.toString(), null));

                latch.countDown();
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
                    RequestResponse requestResponse = new RequestResponse(httpUrl.toString(),
                            httpMethod, response.isSuccessful(), response.message(), respBody);
                    requestResponse.headers = headerMap;
                    requestResponses.add(requestResponse);
                } finally {
                    latch.countDown();
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
