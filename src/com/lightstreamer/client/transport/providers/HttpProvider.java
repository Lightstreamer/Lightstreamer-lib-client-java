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


package com.lightstreamer.client.transport.providers;

import java.util.Map;

import javax.net.ssl.SSLException;

import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.transport.RequestHandle;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.util.threads.ThreadShutdownHook;

/**
 * 
 */
public interface HttpProvider {
    
    public interface HttpRequestListener extends RequestListener {}

    /**
     * MUST NOT BLOCK
     */
  public RequestHandle createConnection(Protocol protocol,
      LightstreamerRequest request, HttpRequestListener httpListener,
      Map<String,String> extraHeaders, Proxy proxy, long tcpConnectTimeout, long tcpReadTimeout) throws SSLException;

  public ThreadShutdownHook getShutdownHook();
  
}
