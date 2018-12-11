//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 10.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.RequestStructureExtraction;

import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.marctarnutzer.jandrolyzer.JSONDeserializer;
import com.marctarnutzer.jandrolyzer.JSONRoot;
import com.marctarnutzer.jandrolyzer.Utils;

import java.util.Map;
import java.util.UUID;

public class JSONStringStrategy {

    private JSONDeserializer jsonDeserializer = new JSONDeserializer();

    public void parse(StringLiteralExpr stringLiteralExpr, String path, Map<String, JSONRoot> jsonModels) {
        String toCheck = Utils.removeEscapeSequencesFrom(stringLiteralExpr.getValue());

        JSONRoot jsonRoot = jsonDeserializer.deserialize(toCheck, path);

        if (jsonRoot != null) {
            jsonRoot.salt = UUID.randomUUID().toString().replace("-", "");
            jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);
        }
    }

}
