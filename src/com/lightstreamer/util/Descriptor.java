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




public abstract class Descriptor implements Cloneable {

  protected Descriptor subDescriptor = null;
  private int length = 0;

  
  public void setSubDescriptor(Descriptor subDescriptor) {
    this.subDescriptor = subDescriptor;
  }
  
  public Descriptor getSubDescriptor() {
    return this.subDescriptor;
  }
  
  public int getSize() {
    return this.length;
  }
  
  public int getFullSize() {
    if (this.subDescriptor != null) {
      return this.getSize() + this.subDescriptor.getSize();
    }
    return this.getSize();
  }
  
  public void setSize(int len) {
    this.length = len;
  }
  
  public abstract int getPos(String name);
  public abstract String getName(int pos);
  public abstract String getComposedString();

  @Override
  public Descriptor clone() {
      try {
          Descriptor copy = (Descriptor) super.clone();
          if (copy.subDescriptor != null) {              
              copy.subDescriptor = subDescriptor.clone();
          }
          return copy;
      } catch (CloneNotSupportedException e) {
          throw new AssertionError(); // should not happen
      }
  }
}
