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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.RequestHandle;
import com.lightstreamer.client.transport.providers.CookieHelper;
import com.lightstreamer.client.transport.providers.HttpProvider;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.threads.ThreadShutdownHook;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 */
public class NettyHttpProvider implements HttpProvider {
  
  private static final String ua;
  static {
    if (LightstreamerClient.LIB_NAME.contains("placeholder")) {
      ua = "Lightstreamer dev client NIO";
    } else {
      ua = LightstreamerClient.LIB_NAME +" "+ LightstreamerClient.LIB_VERSION;
    }
    
  }


  private final SessionThread sessionThread;
  private final HttpPoolManager httpPoolManager;
  
  private static final AtomicInteger objectIdCounter = new AtomicInteger();
  private final int objectId;
  
  public NettyHttpProvider(SessionThread thread) {
      this.sessionThread = thread;
      this.httpPoolManager = SingletonFactory.instance.getHttpPool();
      this.objectId = objectIdCounter.incrementAndGet();
  }
  
  // TEST ONLY
  public NettyHttpProvider(SessionThread thread, HttpPoolManager channelPool) {
      this.sessionThread = thread;
      this.httpPoolManager = channelPool;
      this.objectId = objectIdCounter.incrementAndGet();
  }

  static void debugLogHeaders(HttpHeaders headers, Logger log, String type) {
    if (log.isDebugEnabled() && !headers.isEmpty()) {
      for (CharSequence name : headers.names()) {
        for (CharSequence value : headers.getAll(name)) {
          log.debug(type+" header: " + name + " = " + value);
        }
      }
    }
  }
  
  protected final Logger log = LogManager.getLogger(Constants.TRANSPORT_LOG);
  protected final Logger logPool = LogManager.getLogger(Constants.NETTY_POOL_LOG);
  
  @Override
  public ThreadShutdownHook getShutdownHook() {
      return null; // nothing to do
  }
  
  @Override
  public RequestHandle createConnection(Protocol protocol,
      LightstreamerRequest request, final HttpRequestListener httpListener, Map<String,String> extraHeaders,
      final Proxy proxy, long tcpConnectTimeout, long tcpReadTimeout) throws SSLException {
    String address = request.getTargetServer()+"lightstreamer/"+request.getRequestName()+".txt"+"?LS_protocol=" + Constants.TLCP_VERSION;
    final URI uri;
    try {
       uri = new URI(address);
    } catch (URISyntaxException e) {
      log.fatal("Unexpectedly invalid URI: " + address,e);
      throw new IllegalArgumentException(e);
    }
    
    final boolean secure = isSSL(address);
    final int port = uri.getPort() == -1 ? (secure ? 443 : 80) : uri.getPort();
    
    final FullHttpRequest httpRequest = new DefaultFullHttpRequest(
        HttpVersion.HTTP_1_1, HttpMethod.POST, uri.getRawPath() + "?" + uri.getRawQuery());
    httpRequest.headers().set("Host", uri.getHost());
    
    String cookies = CookieHelper.getCookieHeader(uri);
    if (cookies != null && cookies.length() > 0) {
      httpRequest.headers().set(HttpHeaderNames.COOKIE,cookies);
    }
    
    httpRequest.headers().set(HttpHeaderNames.USER_AGENT, ua);
    httpRequest.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
    
    if (extraHeaders != null) {
      for (Entry<String, String> header : extraHeaders.entrySet()) {
        httpRequest.headers().set(header.getKey(), header.getValue());
      }
    }
    
    //XXX use pooled?
    ByteBuf bbuf = Unpooled.copiedBuffer(request.getTransportAwareQueryString(null, true)+ "\r\n", StandardCharsets.UTF_8);
    httpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes());
    
//    NettyHttpProvider.debugLogHeaders(httpRequest.headers(),log,"Request");
   
    httpRequest.content().clear().writeBytes(bbuf);
    
    NettyFullAddress target = new NettyFullAddress(secure,uri.getHost(),port,proxy);
    
    NettyInterruptionHandler interruptionHandler = new NettyInterruptionHandler();
    
    bind(uri,target,httpListener,httpRequest, interruptionHandler);
    
    return interruptionHandler;
  }
  
  private void bind(URI uri, NettyFullAddress target, HttpRequestListener httpListener, FullHttpRequest httpRequest, NettyInterruptionHandler interruptionHandler) {
      if (log.isDebugEnabled()) {
          log.debug("HTTP transport connection establishing (oid=" + objectId + "): " + format(uri, httpRequest));
      }
      Future<Channel> channelFuture = httpPoolManager.acquire(target);
      FutureBind binding = new FutureBind(uri, httpListener, target,httpRequest, interruptionHandler);
      channelFuture.addListener(binding);
  }
  
  private class FutureBind implements FutureListener<Channel> {
    
    private final HttpRequestListener httpListener;
    private final NettyFullAddress target;
    private final FullHttpRequest httpRequest;
    private final NettyInterruptionHandler interruptionHandler;
    private final URI uri;

    FutureBind(URI uri, HttpRequestListener httpListener, NettyFullAddress target, FullHttpRequest httpRequest, NettyInterruptionHandler interruptionHandler) {
      this.httpListener = httpListener;
      this.target = target;
      this.httpRequest = httpRequest;
      this.interruptionHandler = interruptionHandler;
      this.uri = uri;
    }
    
    private void asyncBind(final URI uri, final NettyFullAddress target, final HttpRequestListener httpListener, final FullHttpRequest httpRequest, final NettyInterruptionHandler interruptionHandler) {
        // add a pause between reconnection attempts to avoid a tight loop
        sessionThread.schedule(new Runnable() {
            
            @Override
            public void run() {
                bind(uri, target, httpListener, httpRequest, interruptionHandler);
            }
        }, 500);
    }

    @Override
    public void operationComplete(Future<Channel> future) throws Exception {
        if (future.isSuccess()) {
            final Channel ch = future.getNow();

            final NettyRequestListener requestListener = new NettyRequestListener(httpListener,target,ch,httpPoolManager);

            if (interruptionHandler.isInterrupted()) {
                if (log.isDebugEnabled()) {
                    log.debug("HTTP transport connection interrupted (oid=" + objectId + "): " + format(uri, httpRequest));
                }
                requestListener.onClosed();
                return;
            }
            
            if (log.isDebugEnabled()) {
                log.debug("HTTP transport connection established (oid=" + objectId + "): " + format(uri, httpRequest));
            }

            NettySocketHandler socketHandler = (NettySocketHandler) PipelineUtils.getChannelHandler(ch);
            if (socketHandler == null) {
                //in a test from altera we got a null pointer, so we also check that 
                //the NettySocketHandler was correctly retrieved
                if (log.isDebugEnabled()) {
                    log.debug("HTTP transport connection error (Wrong socket retrieved from pool, try again) (oid=" + objectId + "): " + format(uri, httpRequest));
                }
                asyncBind(uri,target,httpListener,httpRequest,interruptionHandler);
                
            } else {
                boolean listenerBound = socketHandler.switchListener(uri,requestListener,interruptionHandler);
                if (!listenerBound) {
                    if (log.isDebugEnabled()) {
                        log.debug("HTTP transport connection error (Occupied socket retrieved from pool, try again) (oid=" + objectId + "): " + format(uri, httpRequest));
                    }
                    asyncBind(uri,target,httpListener,httpRequest,interruptionHandler);

                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("HTTP transport sending [" + ch.id() + "]: " + format(httpRequest));
                    }
                    ChannelFuture chf = ch.writeAndFlush(httpRequest);
                    chf.addListener(new ChannelFutureListener() {
                        
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (! future.isSuccess()) {
                                log.error("HTTP write failed [" + ch.id() + "]: " + httpRequest.uri(), future.cause());
                                ch.close();
                                requestListener.onBroken();                                
                            }
                        }
                    });
                }
            }
            
        } else {
            if (interruptionHandler.isInterrupted()) {
                if (log.isDebugEnabled()) {
                    log.debug("HTTP transport connection interrupted (oid=" + objectId + "): " + format(uri, httpRequest));
                }
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("HTTP transport connection error (Couldn't get a socket, try again) (oid=" + objectId + "): " + format(uri, httpRequest));
            }
            asyncBind(uri, target, httpListener, httpRequest, interruptionHandler);
        }
    }
  } // FutureBind
  
  private String format(URI uri, FullHttpRequest req) {
      return format(uri) + "\n" + format(req);
  }
  
  private String format(URI uri) {
      return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
  }
  
  private String format(FullHttpRequest req) {
      return req.uri() + "\n" + req.content().toString(CharsetUtil.UTF_8);
  }

  private static boolean isSSL(String address) {
    return address.toLowerCase().indexOf("https") == 0;
  }

}
