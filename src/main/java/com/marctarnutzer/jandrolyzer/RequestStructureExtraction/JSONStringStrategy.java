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
import com.marctarnutzer.jandrolyzer.JSONRoot;
import com.marctarnutzer.jandrolyzer.TypeEstimator;
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

    public void parse(BinaryExpr binaryExpr, String path, Map<String, JSONRoot> jsonModels) {

        if (!binaryExpr.getParentNode().isPresent() || binaryExpr.getParentNode().get() instanceof BinaryExpr) {
            return;
        }

        System.out.println("Top level BinaryExpr detected: " + binaryExpr.toString());

        String serializedBinaryExpr = serializeBinaryExpr(binaryExpr);

        if (serializedBinaryExpr == null) {
            return;
        }

        JSONRoot jsonRoot = jsonDeserializer.deserialize(Utils.removeEscapeSequencesFrom(serializedBinaryExpr), path);

        if (jsonRoot != null) {
            jsonRoot.salt = UUID.randomUUID().toString().replace("-", "");
            jsonModels.put(jsonRoot.getIdentifier(), jsonRoot);
        }
    }

    public String serializeBinaryExpr(BinaryExpr binaryExpr) {
        String toReturn = null;

        if (!binaryExpr.getOperator().asString().equals("+")) {
            return null;
        }

        Expression leftExpression = binaryExpr.getLeft();
        Expression rightExpression = binaryExpr.getRight();

        if (leftExpression instanceof BinaryExpr) {
            toReturn = serializeBinaryExpr(leftExpression.asBinaryExpr());
        } else if (leftExpression instanceof StringLiteralExpr) {
            toReturn = leftExpression.asStringLiteralExpr().getValue();
        } else {
            toReturn = solvedExpressionType(leftExpression);
        }

        if (rightExpression instanceof BinaryExpr) {
            toReturn = toReturn + serializeBinaryExpr(rightExpression.asBinaryExpr());
        } else if (rightExpression instanceof StringLiteralExpr) {
            toReturn = toReturn + rightExpression.asStringLiteralExpr().getValue();
        } else {
            if (toReturn.endsWith("\"")) {
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
