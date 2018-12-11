//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 10.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Iterator;
import java.util.Map;

public class JSONDeserializer {

    private JsonParser jsonParser = new JsonParser();

    // Returns null if string is not in valid JSON format or if an error occurred
    public JSONRoot deserialize(String jsonString, String path) {
        if (!isValidJSONFormat(jsonString)) {
            return null;
        }

        System.out.println("Valid JSON String detected: " + jsonString);

        JSONRoot jsonRoot = new JSONRoot(path, null, null, null);
        jsonRoot.jsonObject = new JSONObject(JSONDataType.OBJECT, null, null);

        JsonElement jsonElement = jsonParser.parse(jsonString);
        deserialize(jsonElement, jsonRoot.jsonObject);

        return jsonRoot;
    }

    private void deserialize(JsonElement jsonElement, JSONObject jsonObject) {
        if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            Iterator<JsonElement> iterator = jsonArray.iterator();
            while (iterator.hasNext()) {
                JSONObject toInsert = jsonObjectToInsert(iterator.next());

                jsonObject.arrayElementsSet.add(toInsert);
            }
        } else if (jsonElement.isJsonObject()) {
            JsonObject parsedJsonObject = jsonElement.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : parsedJsonObject.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }

                JSONObject toInsert = jsonObjectToInsert(entry.getValue());

                jsonObject.linkedHashMap.put(entry.getKey(), toInsert);
            }

        }
    }

    private JSONObject jsonObjectToInsert(JsonElement jsonElement) {
        JSONObject toInsert = null;

        if (jsonElement.isJsonPrimitive()) {
            if (jsonElement.getAsJsonPrimitive().isString()) {
                if (jsonElement.getAsJsonPrimitive().getAsString().equals("<STRING>")
                        || jsonElement.getAsJsonPrimitive().getAsString().equals("<DOUBLE>")
                        || jsonElement.getAsJsonPrimitive().getAsString().equals("<FLOAT>")
                        || jsonElement.getAsJsonPrimitive().getAsString().equals("<INTEGER>")
                        || jsonElement.getAsJsonPrimitive().getAsString().equals("<BOOLEAN>")
                        || jsonElement.getAsJsonPrimitive().getAsString().equals("<NULL>")) {
                    toInsert = new JSONObject(null, null, jsonElement.getAsJsonPrimitive().getAsString());
                } else {
                    toInsert = new JSONObject(null, jsonElement.getAsJsonPrimitive().getAsString(), null);
                }
            } else if (jsonElement.getAsJsonPrimitive().isNumber()) {
                Number number = jsonElement.getAsJsonPrimitive().getAsNumber();
                if (number.intValue() == number.doubleValue()) {
                    Integer asInteger = number.intValue();
                    toInsert = new JSONObject(null, asInteger, null);
                } else {
                    Double asDouble = number.doubleValue();
                    toInsert = new JSONObject(null, asDouble, null);
                }
            } else if (jsonElement.getAsJsonPrimitive().isBoolean()) {
                toInsert = new JSONObject(null, jsonElement.getAsJsonPrimitive().getAsBoolean(), null);
            }
        } else if (jsonElement.isJsonObject()) {
            toInsert = new JSONObject(JSONDataType.OBJECT, null, null);
            deserialize(jsonElement, toInsert);
        } else if (jsonElement.isJsonArray()) {
            toInsert = new JSONObject(JSONDataType.ARRAY, null, null);
            deserialize(jsonElement, toInsert);
        } else if (jsonElement.isJsonNull()) {
            toInsert = new JSONObject(JSONDataType.NULL, null, null);
        }

        return toInsert;
    }

    private boolean isValidJSONFormat(String jsonString) {
        boolean isValidJSON;

        try {
            new org.json.JSONObject(jsonString);
            isValidJSON = true;
        } catch (JSONException e) {
            isValidJSON = false;
        }

        try {
            new JSONArray(jsonString);
            isValidJSON = true;
        } catch (JSONException e) {
            isValidJSON = isValidJSON || false;
        }

        return isValidJSON;
    }
}
