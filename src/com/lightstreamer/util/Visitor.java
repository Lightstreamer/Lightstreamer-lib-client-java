package com.lightstreamer.util;

/**
 * Visitor of collections.
 * 
 * 
 * @since October 2017
 */
public interface Visitor<T> {
    /**
     * Executes the operation for each element of a collection.
     */
    void visit(T listener);
}