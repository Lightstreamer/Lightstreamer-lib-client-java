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