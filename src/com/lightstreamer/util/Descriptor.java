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
