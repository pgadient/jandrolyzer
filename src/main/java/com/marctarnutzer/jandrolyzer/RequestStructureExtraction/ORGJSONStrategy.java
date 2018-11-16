//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.RequestStructureExtraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.marctarnutzer.jandrolyzer.TypeEstimator;

public class ORGJSONStrategy {

    // extract returns true if a JSON object was successfully extracted from the node
    public boolean extract(Node node) {
        if (node instanceof MethodCallExpr) {
            Expression scopeExpr = ((MethodCallExpr) node).getScope().orElse(null);
            if (scopeExpr != null && (scopeExpr.toString() == "JSONObject" ||
                    scopeExpr.toString() == "org.json.JSONObject")) {
                String estimatedType = TypeEstimator.estimateTypeName(scopeExpr);
                System.out.println("Found type: " + estimatedType + " for expression: " + scopeExpr.toString());
            }
        }

        return false;
    }

}
