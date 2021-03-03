//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package ch.unibe.scg.jandrolyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.util.List;

public class DeclarationLocator {

    public static List<CompilationUnit> compilationUnits;

    /*
     * Locate a declaration or declarator node using the cast node of the resolved declaration.
     * This allows the nodes in the declaration to be resolved once again.
     */
    public static <N extends Node> N locate(N toLocate, Class<N> classToFind) {
        if (compilationUnits == null) {
            return null;
        }

        for (CompilationUnit compilationUnit : compilationUnits) {
            for (N toCheck : compilationUnit.findAll(classToFind)) {
                if (toCheck.equals(toLocate)) {
                    return toCheck;
                }
            }
        }

        return null;
    }
}
