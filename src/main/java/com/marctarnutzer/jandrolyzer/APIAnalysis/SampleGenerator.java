//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 31.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.APIAnalysis;

import com.marctarnutzer.jandrolyzer.Models.JSONDataType;
import com.marctarnutzer.jandrolyzer.Models.JSONObject;
import com.marctarnutzer.jandrolyzer.Models.JSONRoot;
import com.marctarnutzer.jandrolyzer.Models.Project;
import info.debatty.java.stringsimilarity.JaroWinkler;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SampleGenerator {

    public static String populateJSON(JSONRoot jsonRoot, Project project) {
        JaroWinkler jaroWinkler = new JaroWinkler();
        Map<String, Double> variableSimilarities = new HashMap<>();

        variableSimilaritiesInit(variableSimilarities, project);

        populateJSONValues(jsonRoot.jsonObject, variableSimilarities, jaroWinkler, null, project);

        return jsonRoot.formatJSON();
    }

    private static void populateJSONValues(JSONObject jsonObject, Map<String, Double> variableSimilarities,
                                           JaroWinkler jaroWinkler, String key, Project project) {
        if (jsonObject.jsonDataType == JSONDataType.OBJECT) {
            for (Map.Entry<String, JSONObject> jsonObjectEntry : jsonObject.linkedHashMap.entrySet()) {
                populateJSONValues(jsonObjectEntry.getValue(), variableSimilarities, jaroWinkler,
                        jsonObjectEntry.getKey(), project);
            }
        } else if (jsonObject.jsonDataType == JSONDataType.ARRAY) {
            if (!jsonObject.arrayElementsSet.isEmpty()) {
                for (JSONObject arrayJSONObject : jsonObject.arrayElementsSet) {
                    populateJSONValues(arrayJSONObject, variableSimilarities, jaroWinkler, null, project);
                }
            }

            if (!jsonObject.linkedHashMap.isEmpty()) {
                for (Map.Entry<String, JSONObject> jsonObjectEntry : jsonObject.linkedHashMap.entrySet()) {
                    populateJSONValues(jsonObjectEntry.getValue(), variableSimilarities, jaroWinkler,
                            jsonObjectEntry.getKey(), project);
                }
            }
        } else if (jsonObject.value == null && jsonObject.jsonDataType != JSONDataType.NULL && key != null) {
            System.out.println("Key: " + key + ", value: " + jsonObject.jsonDataType);

            switch (jsonObject.jsonDataType) {
                case STRING:
                    for (Map.Entry<String, Double> vs : variableSimilarities.entrySet()) {
                        variableSimilarities.put(vs.getKey(), jaroWinkler.similarity(key, vs.getKey()));
                    }
                    Map<String, Double> similaritiesSorted = variableSimilarities.entrySet()
                            .stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2,
                                    LinkedHashMap::new));
                    Map.Entry<String, Double> entry = similaritiesSorted.entrySet().iterator().next();

                    System.out.println("Most similar to key: " + key + " is value: " + entry.getKey()
                            + ", similarity: " + entry.getValue());
                    String escapedValue = org.json.JSONObject.quote(project.stringVariables.get(entry.getKey())
                            .iterator().next());
                    escapedValue = escapedValue.substring(1, escapedValue.length() - 1);
                    System.out.println("Adding value: " + escapedValue);

                    jsonObject.value = escapedValue;
                    break;
                case BOOLEAN:
                    jsonObject.value = true;
                    break;
                case NUMBER_INT:
                    jsonObject.value = 0;
                    break;
                case NUMBER_DOUBLE:
                    jsonObject.value = 0.0;
                    break;
            }
        }
    }

    private static void variableSimilaritiesInit(Map<String, Double> variableSimilarities, Project project) {
        variableSimilarities.clear();

        for (String v : project.stringVariables.keySet()) {
            variableSimilarities.put(v, 0.0);
        }
    }

}
