//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class JSONObject {

    public JSONDataType jsonDataType;
    public Map<String, JSONObject> linkedHashMap;
    public Set<JSONObject> arrayElementsSet;
    public Object value;

    public JSONObject(JSONDataType jsonDataType, Object value, String type) {
        if (value != null || type != null) {
            String switchValue;
            if (value != null) {
                this.value = value;
                switchValue = value.getClass().getName();
            } else {
                switchValue = type;
            }
            
            switch (switchValue) {
                case "java.lang.String": case "<STRING>":
                    this.jsonDataType = JSONDataType.STRING;
                    break;
                case "java.lang.Double": case "java.lang.Float": case "<DOUBLE>": case "<FLOAT>":
                    this.jsonDataType = JSONDataType.NUMBER_DOUBLE;
                    break;
                case "java.lang.Integer": case "<INTEGER>":
                    this.jsonDataType = JSONDataType.NUMBER_INT;
                    break;
                case "java.lang.Boolean": case "<BOOLEAN>":
                    this.jsonDataType = JSONDataType.BOOLEAN;
                    break;
                case "NULL": case "<NULL>":
                    this.jsonDataType = JSONDataType.NULL;
                    break;
                default:
                    System.out.println("Not a valid datatype: " + switchValue + ", value: " + value);
                    this.jsonDataType = JSONDataType.STRING;
            }
        } else {
            this.linkedHashMap = new LinkedHashMap<>();

            if (jsonDataType != null) {
                this.jsonDataType = jsonDataType;
                if (this.jsonDataType == JSONDataType.ARRAY) {
                    this.arrayElementsSet = new HashSet<>();
                }
            }
        }

    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("JSONDataType: \n" + this.jsonDataType + "\n");
        stringBuilder.append("Value: \n" + this.value + "\n");
        stringBuilder.append("Pairs: \n");
        if (this.linkedHashMap != null) {
            for (Map.Entry<String, JSONObject> jsonObjectEntry : this.linkedHashMap.entrySet()) {
                stringBuilder.append(jsonObjectEntry.getKey() + ":" + jsonObjectEntry.getValue().toString());
            }
        }
        stringBuilder.append("=========================JSONObject=========================\n");
        return stringBuilder.toString();
    }

    public String formatJSON() {
        StringBuilder stringBuilder = new StringBuilder();
        if (this.jsonDataType == JSONDataType.OBJECT) {
            stringBuilder.append("{");

            for (Map.Entry<String, JSONObject> jsonObjectEntry : this.linkedHashMap.entrySet()) {
                stringBuilder.append("\"" + jsonObjectEntry.getKey()+ "\":" + jsonObjectEntry.getValue().formatJSON() + ",");
            }

            stringBuilder.deleteCharAt(stringBuilder.length() - 1);

            stringBuilder.append("}");
        } else if (this.jsonDataType == JSONDataType.ARRAY) {
            stringBuilder.append("[");

            if (!this.arrayElementsSet.isEmpty()) {
                for (JSONObject jsonObject : this.arrayElementsSet) {
                    stringBuilder.append(jsonObject.formatJSON() + ",");
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }

            if (!this.linkedHashMap.isEmpty()) {
                if (!this.arrayElementsSet.isEmpty()) {
                    stringBuilder.append(",");
                }
                for (Map.Entry<String, JSONObject> jsonObjectEntry : this.linkedHashMap.entrySet()) {
                    stringBuilder.append("{\"" + jsonObjectEntry.getKey()+ "\":" + jsonObjectEntry.getValue()
                            .formatJSON() + "},");
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }

            stringBuilder.append("]");
        } else {
            if (value != null) {
                stringBuilder.append("\"" + value.toString() + "\"");
            } else {
                stringBuilder.append("\"<" + this.jsonDataType + ">\"");
            }
        }

        return stringBuilder.toString();
    }

}
