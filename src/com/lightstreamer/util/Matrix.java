/*
 * Copyright (c) 2004-2015 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
 * All rights reserved.
 * www.lightstreamer.com
 *
 * This software is the confidential and proprietary information of
 * Weswit s.r.l.
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Weswit s.r.l.
 */
package com.lightstreamer.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 
 */
public class Matrix<R,C,V> {

  private HashMap<R,HashMap<C,V>> matrix = new HashMap<R,HashMap<C,V>>();
  
  /**
   * Inserts an element in the matrix. If another element is already present in the
   * specified position it is overwritten.
   */
  public void insert(V value, R row, C column) {
    HashMap<C,V> matrixRow = matrix.get(row);
    if (matrixRow == null) {
      matrixRow = new HashMap<C,V>();
      matrix.put(row, matrixRow);
    }
   
    matrixRow.put(column, value);
  }
  
  /**
   * Gets the element at the specified position in the matrix. If the position is empty null is returned.
   */
  public V get(R row,C column) {
    HashMap<C,V> matrixRow = matrix.get(row);
    if (matrixRow != null) {
      return matrixRow.get(column);
    }
    return null;
  }
  
  /**
   * Removes the element at the specified position in the matrix.
   */
  public void del(R row, C column) {
    HashMap<C,V> matrixRow = matrix.get(row);
    if (matrixRow == null) {
      return;
    }
    matrixRow.remove(column);
    if (matrixRow.isEmpty()) {
      //row is empty, get rid of it
      matrix.remove(row);
    }
  }
  
  /**
   * Inserts a full row in the matrix. If another row is already present in the
   * specified position it is overwritten.
   */
  public void insertRow(HashMap<C,V> insRow, R row) {
    matrix.put(row, insRow);
  }
  
  /**
   * @deprecated
   */
  public HashMap<C,V> getRow(R row) {
    return matrix.get(row);
  }
  
  /**
   * Removes the row at the specified position in the matrix.
   */
  public void delRow(R row) {
    matrix.remove(row);
  }
  
  /**
   * @deprecated
   */
  public HashMap<R,HashMap<C,V>> getEntireMatrix() {
    return this.matrix;
  }
  
  /**
   * Verify if there are elements in the grid
   */
  public boolean isEmpty() {
    return matrix.isEmpty();
  }
  
  /**
   * Executes a given callback passing each element of the Matrix. The callback
   * receives the element together with its coordinates. <BR>  
   * Callbacks are executed synchronously before the method returns: calling 
   * insert or delete methods during callback execution may result in 
   * a wrong iteration, return false from the callback to remove the current element.
   */
  public void forEachElement(ElementCallback<R,C,V> callback) {
    //Iterator<String> iterator = list.iterator(); iterator.hasNext();
    for (R row : this.matrix.keySet()) {
      this.forEachElementInRow(row,callback);
    }
  }
  
  /*
   * Executes a given callback passing the key of each row containing at least one element.
   */
  public void forEachRow(RowCallback<R,C,V> callback) {
    Iterator<R> iterator = this.matrix.keySet().iterator();
    while(iterator.hasNext()) {
      R row = iterator.next();
      boolean remove = callback.onRow(row, this.matrix.get(row));
      if (remove) {
        iterator.remove();
      }
    }
  }
  
  /**
   * Executes a given callback passing each element of the specified row. The callback
   * receives the element together with its coordinates. <BR>  
   * Callbacks are executed synchronously before the method returns: calling 
   * insert or delete methods during callback execution may result in 
   * a wrong iteration, return false from the callback to remove the current element.
   */
  public void forEachElementInRow(R row, ElementCallback<R,C,V> callback) {
    HashMap<C,V> rowElements = this.matrix.get(row);
    if (rowElements == null) {
      return;
    }

    Iterator<Entry<C,V>> iterator = rowElements.entrySet().iterator();
    while(iterator.hasNext()) {
      Entry<C,V> entry = iterator.next();
      boolean remove = callback.onElement(entry.getValue(), row, entry.getKey());
      if (remove) {
        iterator.remove();
      }
    }
    
    if (rowElements.isEmpty()) {
      this.matrix.remove(row);
    }
  }
  
  public List<V> sortAndCleanMatrix() {
    LinkedList<V> sorted = new LinkedList<V>();
    
    SortedSet<R> rows = new TreeSet<R>(this.matrix.keySet());
    for (R row : rows) { 
      HashMap<C,V> rowMap = this.matrix.get(row); 
      SortedSet<C> cols = new TreeSet<C>(rowMap.keySet());
      for (C col : cols) { 
        V envelope = rowMap.get(col);
        sorted.add(envelope);
      }
    }
    
    this.matrix.clear();
    return sorted;
  }
  
  public void clear() {
    this.matrix.clear();
  }
  
  public interface ElementCallback<R,C,V> {
    public boolean onElement(V value, R row, C col);
  }
  public interface RowCallback<R,C,V> {
    public boolean onRow(R row, HashMap<C,V> rowMap);
  }
}
