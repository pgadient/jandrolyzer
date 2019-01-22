//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

public enum JSONDataType {
    STRING,
    NUMBER_INT,
    NUMBER_DOUBLE,
    OBJECT, // JSONObject object
    ARRAY, // JSONObject array of JSONObject objects
    BOOLEAN,
    NULL
}
