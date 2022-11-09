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

import io.netty.channel.Channel;

import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.client.transport.providers.HttpProvider.HttpRequestListener;

/**
 * Wraps a {@link HttpRequestListener} and its socket.
 * When the request has been completed, the socket is returned to the pool.
 */
public class NettyRequestListener implements RequestListener {
  
  private HttpRequestListener wrapped;
  private boolean openFired;
  private boolean brokenCalled;
  private boolean closedCalled;
  private NettyFullAddress target;
  private Channel ch;
  private final HttpPoolManager channelPool;

  public NettyRequestListener(HttpRequestListener listener, NettyFullAddress target, Channel ch, HttpPoolManager channelPool) {
    this.wrapped = listener;
    this.target = target;
    this.ch = ch;
    this.channelPool = channelPool;
  }

  @Override
  public void onOpen() {
    if (!this.openFired) {
      this.openFired = true;
      wrapped.onOpen();
    }
  }

  @Override
  public void onBroken() {
    if (!this.brokenCalled && !this.closedCalled) {
      this.brokenCalled = true;
      wrapped.onBroken();
      this.onClosed();
    }
  }

  /**
   * Notifies the closing and releases the channel to the channel pool.
   */
  @Override
  public void onClosed() {
    if (!this.closedCalled) {
      this.closedCalled = true;
      wrapped.onClosed();
      
      channelPool.release(target, ch);
    }
  }

  @Override
  public void onMessage(String message) {
    wrapped.onMessage(message);
  }

}
