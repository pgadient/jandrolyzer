//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 18.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.NoSuchElementException;

public class Utils {

    public static Node getParentClassOrMethod(Node node) {
        Node retNode = null;
        try {
            retNode = node.getParentNode().get();
        } catch (NoSuchElementException e) {
            return null;
        }

        if (retNode instanceof MethodDeclaration || retNode instanceof ClassOrInterfaceDeclaration
                || retNode instanceof ConstructorDeclaration) {
            return retNode;
        } else {
            retNode = getParentClassOrMethod(retNode);
        }

        return retNode;
    }

}
