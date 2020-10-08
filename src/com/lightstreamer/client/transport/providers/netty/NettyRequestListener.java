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
