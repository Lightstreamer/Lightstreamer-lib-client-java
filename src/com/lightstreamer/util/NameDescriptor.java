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

/**
 * 
 */
public class NameDescriptor extends Descriptor {
  
  private String name;

  public NameDescriptor(String name) {
    this.name = name;
  }

  @Override
  public int getPos(String name) {
    if (this.subDescriptor != null) {
      int fromSub = this.subDescriptor.getPos(name);
      return fromSub >-1 ? fromSub+this.getSize() : -1;
    }
    return -1;
  }

  @Override
  public String getName(int pos) {
    if (this.subDescriptor != null) {
      return this.subDescriptor.getName(pos-this.getSize());
    }
    return null;
  }

  @Override
  public String getComposedString() {
    return this.name;
  }
  
  public String getOriginal() {
    return this.name;
  }
  
  @Override
  public NameDescriptor clone() {
      return (NameDescriptor) super.clone();
  }
}
