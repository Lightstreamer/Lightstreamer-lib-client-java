/*
 *  Copyright (c) Lightstreamer Srl
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      https://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
