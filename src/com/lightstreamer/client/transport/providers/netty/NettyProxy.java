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

import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;

import com.lightstreamer.client.Proxy;

/**
 * 
 */
public class NettyProxy extends Proxy {

  
  public NettyProxy(Proxy original) {
    super(original);
  }
  
  public ProxyHandler getProxy() {
    switch(type) {
    
      case "HTTP": 
        if (user != null || password != null) {
          return new HttpProxyHandler(new InetSocketAddress(host,port),user,password);
        } else {
          return new HttpProxyHandler(new InetSocketAddress(host,port));
        }
          
      case "SOCKS4":
        
        if (user != null || password != null) {
          return new Socks4ProxyHandler(new InetSocketAddress(host,port),user);
        } else {
          return new Socks4ProxyHandler(new InetSocketAddress(host,port));
        }
        
      case "SOCKS5":
    
        if (user != null || password != null) {
          return new Socks5ProxyHandler(new InetSocketAddress(host,port),user,password);
        } else {
          return new Socks5ProxyHandler(new InetSocketAddress(host,port));
        }
        
      default:
        return null;
    }
  }

  
  
}
