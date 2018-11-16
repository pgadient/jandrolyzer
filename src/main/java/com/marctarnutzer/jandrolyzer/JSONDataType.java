//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

public enum JSONDataType {
    STRING,
    NUMBER_INT,
    NUMBER_FLOAT,
    OBJECT, // JSON object
    ARRAY, // JSON array of JSON objects
    BOOLEAN,
    NULL
}
