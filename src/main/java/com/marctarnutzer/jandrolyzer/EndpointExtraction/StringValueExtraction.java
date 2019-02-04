//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 23.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.EndpointExtraction;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.marctarnutzer.jandrolyzer.Models.Project;

import java.util.List;

public class StringValueExtraction {

    public static List<String> extract(BinaryExpr binaryExpr, Project project) {
        if (!binaryExpr.getParentNode().isPresent() || binaryExpr.getParentNode().get() instanceof BinaryExpr) {
            return null;
        }

        System.out.println("Detected top level BinaryExpr: " + binaryExpr);

        List<String> serializedBinaryExprs = ExpressionValueExtraction.serializeBinaryExpr(binaryExpr, null);

        return serializedBinaryExprs;
    }

    // Check if a new StringBuilder object is created and check if it contains API endpoint information
    public static List<String> extract(VariableDeclarator variableDeclarator, Project project) {
        if (variableDeclarator.getInitializer().isPresent() && variableDeclarator.getInitializer().get()
                .isObjectCreationExpr() && variableDeclarator.getInitializer().get().asObjectCreationExpr().getType()
                .getName().asString().equals("StringBuilder")) {
                /*
                 * Check if the assembled StringBuilder strings contain valid API URLs
                 */
                List<String> potentialApiURLs = ExpressionValueExtraction.extractStringBuilderValue(variableDeclarator, project);

                return potentialApiURLs;

        }

        return null;
    }

    /*
     * Extract API URLs from concatenated Strings using the concat() MethodCallExpr
     */
    public static List<String> extract(MethodCallExpr methodCallExpr, Project project) {
        if (methodCallExpr.getParentNode().isPresent() && methodCallExpr.getParentNode().get() instanceof MethodCallExpr
                && ((MethodCallExpr) methodCallExpr.getParentNode().get()).asMethodCallExpr().getName().asString()
                .equals("concat")) {
            return null;
        }

        System.out.println("Detected rightmost concat method: " + methodCallExpr);

        List<String> stringsToCheck = ExpressionValueExtraction.extractStringConcatValue(methodCallExpr, null);

        return stringsToCheck;
    }
}
