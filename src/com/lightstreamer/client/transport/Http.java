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
package com.lightstreamer.client.transport;

import java.util.Map;

import javax.net.ssl.SSLException;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.protocol.TextProtocol;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.providers.HttpProvider;
import com.lightstreamer.client.transport.providers.HttpProvider.HttpRequestListener;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;


public class Http implements Transport {
  
  protected final Logger log = LogManager.getLogger(Constants.TRANSPORT_LOG);
  
  private final HttpProvider httpProvider;
  
  private final SessionThread sessionThread;

  public Http(SessionThread thread, HttpProvider httpProvider) {
    this.sessionThread = thread;
    this.httpProvider = httpProvider;
    this.sessionThread.registerShutdownHook(httpProvider.getShutdownHook());
  }
  
    @Override
    public RequestHandle sendRequest(Protocol protocol, final LightstreamerRequest request, final RequestListener protocolListener, Map<String, String> extraHeaders, Proxy proxy, long tcpConnectTimeout, long tcpReadTimeout) {
        if (httpProvider == null) {
            log.fatal("There is no default HttpProvider, can't connect");
            return null;
        }
//        if (log.isDebugEnabled()) {
//            log.debug("HTTP transport sending: " + request.getRequestName() + " " + request.getTransportAwareQueryString(null, true));
//        }
        final RequestHandle connection;
        try {
            HttpRequestListener httpListener = new MyHttpListener(protocolListener, request, sessionThread);
            connection = httpProvider.createConnection(protocol, request, httpListener, extraHeaders,
                    proxy, tcpConnectTimeout, tcpReadTimeout);
        } catch (SSLException e) {
            sessionThread.queue(new Runnable() {
                public void run() {
                    protocolListener.onBroken();
                }
            });
            return null;
        }
        if (connection == null) {
            // we expect that a closed/broken event will be fired soon
            return null;
        }

        return new RequestHandle() {
            @Override
            public void close(boolean forceConnectionClose) {
                connection.close(forceConnectionClose);
            }
        };
    }
  
    /**
     * Wraps a request listener created by {@link TextProtocol} and forwards the calls to the session thread.
     */
  private static class MyHttpListener implements HttpRequestListener {

    private final RequestListener listener;
    private final LightstreamerRequest request;
    private final SessionThread sessionThread;

    public MyHttpListener(RequestListener listener, LightstreamerRequest request, SessionThread sessionThread) {
      this.listener = listener;
      this.request = request;
      this.sessionThread = sessionThread;
    }
    
    LightstreamerRequest getLightstreamerRequest() {
      return request;
    }
    
    @Override
    public void onMessage(final String message) {
      sessionThread.queue(new Runnable() {
        public void run() {
          listener.onMessage(message);
        }
      });
    }

    @Override
    public void onOpen() {
      sessionThread.queue(new Runnable() {
        public void run() {
          listener.onOpen();
        }
      });
    }

    @Override
    public void onClosed() {
      sessionThread.queue(new Runnable() {
        public void run() {
            listener.onClosed();
        }
      });
    }

    @Override
    public void onBroken() {
      sessionThread.queue(new Runnable() {
        public void run() {
          listener.onBroken();
        }
      });
    }
  }

}
