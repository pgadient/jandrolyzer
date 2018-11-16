//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import java.util.LinkedHashMap;
import java.util.Map;

public class JSON {

    public JSONDataType jsonDataType;
    public Map<String, JSON> linkedHashMap;
    public Object value;

    public JSON(JSONDataType jsonDataType, Boolean isSingleTypeOrValue) {
        this.jsonDataType = jsonDataType;
        if (!isSingleTypeOrValue) {
            this.linkedHashMap = new LinkedHashMap<>();
        }
    }

}
