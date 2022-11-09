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
