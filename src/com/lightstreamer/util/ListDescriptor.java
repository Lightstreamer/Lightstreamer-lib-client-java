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

import java.util.HashMap;
import java.util.Map;

/**
 * 
 */
public class ListDescriptor extends Descriptor {

  private String[] list;
  private Map<String,Integer> reverseList;
  
  public ListDescriptor(String[] list) {
    this.list = list;
    this.reverseList = getReverseList(list);
    super.setSize(list.length);
  }
  
  private static Map<String,Integer> getReverseList(String[] list) {
    Map<String,Integer> reverseList = new HashMap<String,Integer>();
    for (int i=0; i<list.length; i++) {
      reverseList.put(list[i], i+1);
    }
    return reverseList;
  }
  
  
  @Override
  public void setSize(int len) {
    //can't manually set the size in the list case
  }
  
  
  @Override
  public String getComposedString() {
    //return String.join(" ", list); Java 8 :(
  	  
  	if (list.length <= 0) {
  	  return "";
  	}
  	  
  	StringBuilder joined = new StringBuilder(list[0]);
  	for (int i=1; i<list.length; i++) {
  	  joined.append(" ").append(list[i]);
  	}
  	
  	return joined.toString();
	  
  }
  
  @Override
  public int getPos(String name) {
    if(this.reverseList.containsKey(name)) {
      return this.reverseList.get(name);
    } else if (this.subDescriptor != null) {
      int fromSub = this.subDescriptor.getPos(name);
      return fromSub > -1 ? fromSub+this.getSize() : -1;
    }
    return -1;
  }
  
  @Override
  public String getName(int pos) {
    if (pos > this.getSize()) {
      if (this.subDescriptor != null) {
        return this.subDescriptor.getName(pos-this.getSize());
      }
    } else if (pos >= 1) {
      return this.list[pos-1];
    }
    
    return null;
  }
  
  public String[] getOriginal() {
    return this.list;
  }
  
  
  private static final String NO_EMPTY = " name cannot be empty";
  private static final String NO_SPACE = " name cannot contain spaces";
  private static final String NO_NUMBER = " name cannot be a number";

  public static void checkItemNames(String[] names, String head) {
    if (names == null) {
      return;
    }
    for (int i = 0; i < names.length; i++) {
      if (names[i] == null || names[i].equals("")) {
        throw new IllegalArgumentException(head+NO_EMPTY);
        
      } else if (names[i].indexOf(" ") > -1) {
        // An item name cannot contain spaces
        throw new IllegalArgumentException(head+NO_SPACE);
        
      } else if (Number.isNumber(names[i])) {
        // An item name cannot be a number
        throw new IllegalArgumentException(head+NO_NUMBER);
      }
    }
  }

  public static void checkFieldNames(String[] names, String head) {
    if (names == null) {
      return;
    }
    for (int i = 0; i < names.length; i++) {
      if (names[i] == null || names[i].equals("")) {
        throw new IllegalArgumentException(head+NO_EMPTY);
        
      } else if (names[i].indexOf(" ") > -1) {
        // A field name cannot contain spaces
        throw new IllegalArgumentException(head+NO_SPACE);
      }
    }
  }
  
  @Override
  public ListDescriptor clone() {
      ListDescriptor copy = (ListDescriptor) super.clone();
      copy.list = list.clone();
      copy.reverseList = new HashMap<>(reverseList);
      return copy;
  }
}
