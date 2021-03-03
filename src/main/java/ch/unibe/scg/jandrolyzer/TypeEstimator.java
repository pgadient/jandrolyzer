//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 16.11.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package ch.unibe.scg.jandrolyzer;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Arrays;
import java.util.HashSet;

public class TypeEstimator {

    public static String estimateTypeName(Expression expr) {
        String typeString = null;

        try {
            ResolvedType resolvedType = expr.calculateResolvedType();

            if (resolvedType.isPrimitive()) {
                typeString = resolvedType.asPrimitive().getBoxTypeQName();
            } else {
                typeString = resolvedType.asReferenceType().getQualifiedName();
            }
        } catch (UnsolvedSymbolException e) {
            typeString = e.getName();
            System.out.println("[NOT RESOLVED] was no resolved but estimated to type: " + typeString + " Exception: " + e);
        }  catch (Exception e) {
            typeString = null;

            if (expr instanceof ObjectCreationExpr) {
                typeString = ((ObjectCreationExpr) expr).getType().asString();
            }

            System.out.println("[NOT RESOLVED] Exception: " + e);
        }

        return typeString;
    }

    public static boolean extendsCollection(String typeString) {
        return collectionHashSet.contains(typeString);
    }

    // TODO: extend this set with all interfaces and classes which extend Collection
    static HashSet<String> collectionHashSet = new HashSet<>(Arrays.asList(
            "java.util.List",
            "java.util.Vector",
            "java.util.AbstractSequentialList",
            "java.util.AbstractList",
            "java.util.AbstractQueue",
            "java.util.AbstractSet",
            "java.util.ArrayDeque",
            "java.util.ConcurrentLinkedDeque",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.concurrent.ArrayBlockingQueue",
            "java.util.concurrent.ConcurrentLinkedQueue",
            "java.util.concurrent.DelayQueue",
            "java.util.concurrent.LinkedBlockingDeque",
            "java.util.concurrent.LinkedTransferQueue",
            "java.util.concurrent.PriorityBlockingQueue",
            "java.util.PriorityQueue",
            "java.util.concurrent.SynchronousQueue",
            "java.util.concurrent.BlockingDeque",
            "java.util.concurrent.TransferQueue",
            "java.util.concurrent.BlockingQueue",
            "java.util.Deque",
            "java.util.NavigableSet",
            "java.util.SortedSet",
            "java.util.TreeSet",
            "java.util.concurrent.ConcurrentSkipListSet",
            "java.util.concurrent.CopyOnWriteArraySet",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.Set",
            "java.util.Queue",
            "java.util.AbstractCollection",
            "java.util.concurrent.CopyOnWriteArrayList",
            "java.util.concurrent.LinkedBlockingQueue",
            "java.util.Stack",
            "java.util.Map"));

}
