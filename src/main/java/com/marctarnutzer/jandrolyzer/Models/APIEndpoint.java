//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

import java.util.HashMap;
import java.util.Map;

public class APIEndpoint {

    public String path;
    public HashMap<String, String> queries = new HashMap<>();

    public APIEndpoint(String path) {
        this.path = path;
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("  Path: " + this.path + "\n");
        stringBuilder.append("  Queries: \n");
        for (Map.Entry entry: this.queries.entrySet()) {
            stringBuilder.append("      Query key: " + entry.getKey() + ", query value: " + entry.getValue() + "\n");
        }
        return stringBuilder.toString();
    }
}
