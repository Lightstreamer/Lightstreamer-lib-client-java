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
