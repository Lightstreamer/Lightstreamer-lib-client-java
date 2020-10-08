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
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.transport.providers.CookieHelper;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

/**
 * HTTP channel handler notified when the underlying channel generates an event 
 * (e.g. the channel is active, there is new data to read etc.).
 */
public class NettySocketHandler extends SimpleChannelInboundHandler<Object> implements Closeable {
  

  private static final int INIT = 0;
  private static final int OPEN = 1;
  private static final int OCCUPIED = 2;
  private static final int CLOSED = 3;


  protected static final Logger log = LogManager.getLogger(Constants.TRANSPORT_LOG);
  protected static final Logger logPool = LogManager.getLogger(Constants.NETTY_POOL_LOG);
  private URI uri;
  
  private NettyRequestListener socketListener = null;
  
  private int status = INIT;
  private NettyInterruptionHandler interruptionHandler;
  private LineAssembler lineAssembler;
  private final AtomicReference<Channel> channelRef = new AtomicReference<>();
  
  /**
   * The flag is false when the server sends the header "Connection: close"
   * and it is true when the header is "Connection: keep-alive".
   */
  private volatile boolean keepalive = false;
  
  private void set(int status) {
   this.status = status;
  }

  private boolean is(int status) {
    return this.status == status;
  }
  private boolean isNot(int status) {
    return !this.is(status);
  }
  
  /**
   * Binds the request listener with the socket so that the opening/reading/closing events of the socket are notified to the listener.
   * Returns false if the socket can't be bound.
   */
  public synchronized boolean switchListener(URI uri, NettyRequestListener socketListener, NettyInterruptionHandler interruptionHandler) {
    if (this.is(OPEN)) {
      socketListener.onOpen();
    } else if (this.is(OCCUPIED) || this.is(CLOSED)) {
      //should never happen! what do we do? XXX
      return false;
    } //else if is init we'll get the onOpen later
    
    this.uri = uri;
    this.socketListener = socketListener;
    switchInterruptionHandler(interruptionHandler);
    this.lineAssembler = new LineAssembler(socketListener);
    return true;
  }
  
  void switchInterruptionHandler(NettyInterruptionHandler newInterruptionHandler) {
      // Only one interruptionHandler should point to this socketHandler
      // If the old interruptionHandler holds a link, it must be deleted before assigning the new interruptionHandler
      if (this.interruptionHandler != null) {          
          this.interruptionHandler.connectionRef.set(null);
      }
      newInterruptionHandler.connectionRef.set(this);
      this.interruptionHandler = newInterruptionHandler;
  }
  
  private void error(Channel ch) {
    
    if (this.socketListener != null) {
      this.socketListener.onBroken();
    }
    this.socketListener = null;
    this.interruptionHandler = null;
    
    this.set(CLOSED); //we'll be closed soon anyway
    
    closeChannel(ch);
    
  }
  
  private void dispose() {
    
    if (this.socketListener != null) {
      this.socketListener.onClosed();
    }
    this.socketListener = null;
    this.interruptionHandler = null;
    
    
    this.set(CLOSED); //we'll be closed soon anyway
  }
  
  private void reuse(ChannelHandlerContext ctx) {
    
    if (! keepalive) {
        closeChannel(ctx.channel());
    }
    
    if (this.socketListener != null) {
      this.socketListener.onClosed();
    }
    this.socketListener = null;
    this.interruptionHandler = null;
    this.keepalive = false;
    
    
    this.set(OPEN);
  }
  
  private void open() {
    this.set(OPEN);
    if (this.socketListener != null) {
      this.socketListener.onOpen();
    }
  }
  
  private void message(ByteBuf buf) {
//    if (this.socketListener != null) {
//      this.socketListener.onMessage(message);
//    }
      lineAssembler.readBytes(buf);
  }
  
  private boolean isInterrupted() {
    if (this.interruptionHandler != null) {
      return this.interruptionHandler.isInterrupted();
    }
    return false;
  }

  @Override
  public synchronized void channelActive(ChannelHandlerContext ctx) {
    Channel ch = ctx.channel();
    channelRef.set(ch);
    if (logPool.isDebugEnabled()) {
        logPool.debug("HTTP channel active [" + ch.id() + "]");
    }
    
    if (this.isNot(INIT)) {
      //something wrong
      error(ch);
    } else {
      open();
      
      if (this.isInterrupted()) {
        closeChannel(ch);
      }
      
    }
  }


  @Override
  public synchronized void channelInactive(ChannelHandlerContext ctx) {
    Channel ch = ctx.channel();
    if (logPool.isDebugEnabled()) {
        logPool.debug("HTTP channel inactive [" + ch.id() + "]");
    }
    
    if (this.is(OCCUPIED)) {
      //if we're still occupied this means the sudden inactivity is not good
      error(ch);
    } else {
      dispose();
    }
  }
  
  public synchronized void messageReceived(ChannelHandlerContext ctx, Object msg) {
    Channel ch = ctx.channel();
    if (this.isInterrupted()) {
      if (!(msg instanceof LastHttpContent)) {
        log.info("Force socket close [" + ch.id() + "]");
        closeChannel(ch);
      } 
    }
  
    if (msg instanceof HttpResponse) {
      if (this.isNot(OPEN)) {
        error(ch);
        return;
      }
      this.set(OCCUPIED);
      
      HttpResponse response = (HttpResponse) msg;
      
      int respCode = response.status().code();
      if (log.isDebugEnabled()) {
        log.debug("Response status: " + respCode);
//        log.debug("Response version: " + response.protocolVersion());
      }
//      NettyHttpProvider.debugLogHeaders(response.headers(),log,"Response");
      
      
      if (respCode < 200) {
        //100 family, not expected
        error(ch);
        return;
      } else if (respCode < 300) {
        //200 family: all is good
      } else if (respCode < 400) {
        //300 family: TODO 1.1 handling redirections
        log.warn("Redirection currently not implemented");
        //String target = response.headers().get(HttpHeaderNames.LOCATION)
        error(ch);
        return;
      } else {
        //400 - 500 families: problems
        error(ch);
        return;
      }
      
      keepalive = HttpUtil.isKeepAlive(response);

      for (String cookie : response.headers().getAll(HttpHeaderNames.SET_COOKIE)) {
          CookieHelper.saveCookies(uri, cookie);
      }
        
    } else if (msg instanceof HttpContent) {
      if (this.isNot(OCCUPIED)) {
        error(ch);
        return;
      }
      
      HttpContent chunk = (HttpContent) msg;
      ByteBuf buf = chunk.content();
      
      if (log.isDebugEnabled()) {
          if (buf.readableBytes() > 0) {
              log.debug("HTTP transport receiving [" + ch.id() + "]:\n" + buf.toString(CharsetUtil.UTF_8));
          }
      }
      message(buf);
       
      if (chunk instanceof LastHttpContent) {
        //http complete, go back to open so that it can be reused
        reuse(ctx);
      }
        
    }
  }
  
  

  @SuppressWarnings("null")
  @Override
  public synchronized void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      Channel ch = ctx.channel();
      log.error("HTTP transport error [" + ch.id() + "]", cause);
      error(ch);
  }

  @Override
  protected synchronized void channelRead0(ChannelHandlerContext ctx, Object msg)
      throws Exception {
    messageReceived(ctx,msg);
  }
  
  void closeChannel(Channel ch) {
      if (log.isDebugEnabled()) {          
          log.debug("Channel closed [" + ch.id() + "]");
      }
      ch.close();
  }
  
  @Override
  public void close() {
      Channel ch = channelRef.get();
      if (ch != null) {
          error(ch);
      }
  }
  
}