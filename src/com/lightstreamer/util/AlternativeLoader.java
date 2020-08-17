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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * 
 */
public abstract class AlternativeLoader<T> {
  
  
  protected abstract String[] getDefaultClassNames();
  
  private T loadImplementation(String className) {
    try {
      Class<?> implClass = Class.forName(className);
      Constructor<?>[] constructors = implClass.getConstructors();
      if (constructors.length == 1) {
        return (T) constructors[0].newInstance();
      }
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
    }
    return null;
  }
  
  public T getAlternative() {
    String[] alternatives = this.getDefaultClassNames();
    for (int i=0; i<alternatives.length; i++) {
      
      T internal = this.loadImplementation(alternatives[i]);
      if (internal != null) {
        return internal;
      }
      
    }
    
    return null;
  }
    

}
