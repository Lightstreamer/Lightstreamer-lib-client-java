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
