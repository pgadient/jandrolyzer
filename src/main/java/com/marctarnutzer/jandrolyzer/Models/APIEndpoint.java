//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

import java.util.HashMap;

public class APIEndpoint {

    String path;
    public HashMap<String, String> queries;

    public APIEndpoint(String path) {
        this.path = path;
    }

}
