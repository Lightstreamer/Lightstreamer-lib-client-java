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
package com.lightstreamer.client.transport.providers.netty;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import com.lightstreamer.client.transport.RequestHandle;

class NettyInterruptionHandler implements RequestHandle {

  private boolean interrupted = false;
  final AtomicReference<Closeable> connectionRef = new AtomicReference<>(); // written by Netty thread but read by Session thread
  
  public NettyInterruptionHandler() {
      super();
  }

  @Override
  public void close(boolean forceConnectionClose) {
      this.interrupted  = true;
      if (forceConnectionClose) {
          Closeable ch = connectionRef.get();
          if (ch != null) {              
              try {
                  ch.close();
              } catch (IOException e) {
                  // ignore
              }
          }
      }
  }
  
  
  boolean isInterrupted() {
    return this.interrupted;
  }
  
}