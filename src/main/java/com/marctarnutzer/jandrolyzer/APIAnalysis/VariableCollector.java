//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 31.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer.APIAnalysis;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.marctarnutzer.jandrolyzer.Models.Project;

import java.util.HashSet;
import java.util.Set;

public class VariableCollector {

    /*
     * Collects variable name & value of String variable declarations and adds them to the project
     */
    public static void collect(VariableDeclarator variableDeclarator, Project project) {
        if (!(variableDeclarator.getType().isClassOrInterfaceType() && variableDeclarator.getType()
                .asClassOrInterfaceType().getName().asString().equals("String")
                && variableDeclarator.getInitializer().isPresent()
                && variableDeclarator.getInitializer().get().isStringLiteralExpr())) {
            return;
        }

        String variableName = variableDeclarator.getNameAsString();
        String variableValue = variableDeclarator.getInitializer().get().asStringLiteralExpr().getValue();

        System.out.println("Adding String variable with name: " + variableName + ", value: " + variableValue);

        if (project.stringVariables.containsKey(variableName)) {
            project.stringVariables.get(variableName).add(variableValue);
        } else {
            Set<String> valueSet = new HashSet<>();
            valueSet.add(variableValue);
            project.stringVariables.put(variableName, valueSet);
        }
    }
}
