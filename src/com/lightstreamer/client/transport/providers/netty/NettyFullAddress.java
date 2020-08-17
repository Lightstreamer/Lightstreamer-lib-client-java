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

import io.netty.handler.proxy.ProxyHandler;

import java.net.InetSocketAddress;
import java.util.Objects;

import com.lightstreamer.client.Proxy;


public class NettyFullAddress {

  private final InetSocketAddress addressObj;
  private final Proxy proxy;
  private final boolean secure;
  private final String host;
  private final int port;


  public NettyFullAddress(boolean secure, String host, int port, Proxy proxy) {
    // let the transport layer resolve the address
    this.addressObj =  InetSocketAddress.createUnresolved(host, port);
    this.proxy = proxy;
    
    this.host = host;
    this.port = port;
    this.secure = secure;
  }

  public InetSocketAddress getAddress() {
    return addressObj;
  }
  
  public boolean isSecure() {
    return this.secure;
  }
  
  public ProxyHandler getProxy() {
    if (proxy == null) {
      return null;
    }
    return new NettyProxy(proxy).getProxy();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    NettyFullAddress check = (NettyFullAddress) obj;
    if ((this.proxy == null && check.proxy == null) || (this.proxy != null && this.proxy.equals(check.proxy))) {
      return addressObj.equals(check.addressObj);
    }
    return false;
  }
  
  @Override 
  public int hashCode() {
    return Objects.hash(this.proxy, this.addressObj);
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }
}
