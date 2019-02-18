//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 10.12.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.RequestStructureExtraction;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.marctarnutzer.jandrolyzer.JSONDeserializer;
import com.marctarnutzer.jandrolyzer.Main;
import com.marctarnutzer.jandrolyzer.Models.JSONRoot;
import com.marctarnutzer.jandrolyzer.Models.Project;
import com.marctarnutzer.jandrolyzer.TypeEstimator;
import com.marctarnutzer.jandrolyzer.Utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JSONStringStrategy {

    private JSONDeserializer jsonDeserializer = new JSONDeserializer();

    public boolean extract(List<String> potentialJSONStrings, Project project, String libraryName, String path) {
        boolean foundValidJSON = false;

        for (String potentialJSONString : potentialJSONStrings) {
            System.out.println("Checking for JSON: " + potentialJSONString);
            foundValidJSON = parse(potentialJSONString, path, project.jsonModels, libraryName)
                    || foundValidJSON;
        }

        return foundValidJSON;
    }

    /*
     * Parses a StringLiteralExpr for a valid JSON model
     * Returns boolean value whether valid JSON model was found or not
     */
    public boolean parse(StringLiteralExpr stringLiteralExpr, String path, Map<String, JSONRoot> jsonModels) {
        return parse(stringLiteralExpr.getValue(), path, jsonModels, "noLib.StringLiteralExpr");
    }

    public boolean parse(String potJSONString, String path, Map<String, JSONRoot> jsonModels, String libraryName) {
        String toCheck = Utils.removeEscapeSequencesFrom(potJSONString);

        JSONRoot jsonRoot = jsonDeserializer.deserialize(toCheck, path);

        if (jsonRoot != null) {
            jsonRoot.salt = UUID.randomUUID().toString().replace("-", "");
            jsonRoot.library = libraryName;

            boolean isDuplicate = false;
            for (Map.Entry<String, JSONRoot> entry : jsonModels.entrySet()) {
                if (entry.getValue().formatJSON().equals(toCheck)) {
                    isDuplicate = true;
                }
            }

            if (!isDuplicate) {
                jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);
            }
        } else {
            return false;
        }

        return true;
    }

    /*
     * Parses a top level BinaryExpr for a valid JSON model
     * Returns boolean value whether valid JSON model was found or not
     */
    public boolean parse(BinaryExpr binaryExpr, String path, Map<String, JSONRoot> jsonModels) {
        if (!binaryExpr.getParentNode().isPresent() || binaryExpr.getParentNode().get() instanceof BinaryExpr) {
            return false;
        }

        System.out.println("Top level BinaryExpr detected: " + binaryExpr.toString());

        String serializedBinaryExpr = serializeBinaryExpr(binaryExpr, 0);

        if (serializedBinaryExpr == null) {
            return false;
        }

        JSONRoot jsonRoot = jsonDeserializer.deserialize(Utils.removeEscapeSequencesFrom(serializedBinaryExpr), path);

        if (jsonRoot != null) {
            jsonRoot.salt = UUID.randomUUID().toString().replace("-", "");
            jsonRoot.library = "noLib.BinaryExpr";
            jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);

            return true;
        } else {
            return false;
        }
    }

    public String serializeBinaryExpr(BinaryExpr binaryExpr, int depthLevel) {
        if (Main.maxRecursionDepth != -1 && Main.maxRecursionDepth <= depthLevel) {
            System.out.println("Max depth reached.");
            return null;
        }
        depthLevel++;

        String toReturn = null;

        if (!binaryExpr.getOperator().asString().equals("+")) {
            return null;
        }

        Expression leftExpression = binaryExpr.getLeft();
        Expression rightExpression = binaryExpr.getRight();

        if (leftExpression instanceof BinaryExpr) {
            toReturn = serializeBinaryExpr(leftExpression.asBinaryExpr(), depthLevel);
        } else if (leftExpression instanceof StringLiteralExpr) {
            toReturn = leftExpression.asStringLiteralExpr().getValue();
        } else {
            toReturn = solvedExpressionType(leftExpression);
        }

        if (rightExpression instanceof BinaryExpr) {
            toReturn = toReturn + serializeBinaryExpr(rightExpression.asBinaryExpr(), depthLevel);
        } else if (rightExpression instanceof StringLiteralExpr) {
            toReturn = toReturn + rightExpression.asStringLiteralExpr().getValue();
        } else {
            if (toReturn != null && toReturn.endsWith("\"")) {
                toReturn = toReturn + solvedExpressionType(rightExpression);
            } else {
                toReturn = toReturn + "\"" + solvedExpressionType(rightExpression) + "\"";
            }
        }

        return toReturn;
    }

    private String solvedExpressionType(Expression expression) {
        String estimatedType = TypeEstimator.estimateTypeName(expression);

        System.out.println("Expression: " + expression.toString() + ", type: " + estimatedType);

        if (estimatedType == null) {
            return null;
        }

        switch (estimatedType) {
            case "java.lang.String":
                return "<STRING>";
            case "java.lang.Double": case "java.lang.Float":
                return "<DOUBLE";
            case "java.lang.Integer":
                return "<INTEGER>";
            case "java.lang.Boolean":
                return "<BOOLEAN>";
            default:
                return "<UNKNOWN>";
        }
    }
}
