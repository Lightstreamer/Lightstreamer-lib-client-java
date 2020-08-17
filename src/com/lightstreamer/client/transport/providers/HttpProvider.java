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
