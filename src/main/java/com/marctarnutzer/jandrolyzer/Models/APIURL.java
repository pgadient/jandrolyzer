//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 15.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.Models;

import java.util.HashSet;

public class APIURL {

    public String scheme;
    public String authority;
    public HashSet<APIEndpoint> endpoints;

    public APIURL(String scheme) {
        this.scheme = scheme;
    }

    public String getBaseURL() {
        return scheme + authority;
    }

}
