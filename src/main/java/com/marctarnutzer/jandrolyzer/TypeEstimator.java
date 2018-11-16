//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;

public class TypeEstimator {

    public static String estimateTypeName(Expression expr) {
        String typeString = null;

        //System.out.println("Code: " + expr.toString());

        try {
            if (expr instanceof MethodCallExpr) {
                ResolvedType resolvedType = expr.calculateResolvedType();
                typeString = resolvedType.asReferenceType().getQualifiedName();

                //System.out.println("[RESOLVED] MethodCallExpr was resolved to: " + typeString);
            } else if (expr instanceof ObjectCreationExpr) {
                ResolvedType resolvedType = expr.calculateResolvedType();
                typeString = resolvedType.asReferenceType().getQualifiedName();

                //System.out.println("[RESOLVED] ObjectCreationExpr was resolved to: " + typeString);
            } else if (expr instanceof CastExpr) {
                typeString = expr.calculateResolvedType().asReferenceType().getQualifiedName();

                //System.out.println("[RESOLVED] CastExpr was resolved to: " + typeString);
            } else if (expr instanceof NameExpr) {
                ResolvedType resolvedType = expr.calculateResolvedType();
                typeString = resolvedType.asReferenceType().getQualifiedName();

                //System.out.println("[RESOLVED] NameExpr was resolved to : " + typeString);
            } else {
                //System.out.println("Expression is a: " + expr.getClass());
            }
        } catch (UnsolvedSymbolException e) {
            typeString = e.getName();
            System.out.println("[NOT RESOLVED] was no resolved but estimated to type: " + typeString + " Exception: " + e);
        }  catch (Exception e) {
            typeString = null;
            //System.out.println("[NOT RESOLVED] Exception: " + e);
        }

        return typeString;
    }

}
