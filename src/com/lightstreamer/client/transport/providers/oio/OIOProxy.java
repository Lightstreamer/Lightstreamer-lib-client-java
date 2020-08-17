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
package com.lightstreamer.client.transport.providers.oio;

import java.net.InetSocketAddress;

import com.lightstreamer.client.Proxy;

class OIOProxy extends Proxy {

  OIOProxy(Proxy original) {
    super(original);
  }

  /**
   * TODO 1.1 this version does not support authentication, unless a global Authenticator is used
   */
  public java.net.Proxy getProxy() {

    switch(type) {
      case "HTTP": 
        return new java.net.Proxy(java.net.Proxy.Type.HTTP,new InetSocketAddress(host,port));
          
      case "SOCKS4":
      case "SOCKS5":
        return new java.net.Proxy(java.net.Proxy.Type.SOCKS,new InetSocketAddress(host,port));
        
      default:
        return null;
    }
  }
  
}
