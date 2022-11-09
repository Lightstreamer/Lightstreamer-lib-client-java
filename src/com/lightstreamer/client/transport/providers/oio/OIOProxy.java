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
