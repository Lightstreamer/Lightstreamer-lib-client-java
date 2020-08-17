/*
 * Copyright (c) 2004-2007 Weswit s.r.l., Via Campanini, 6 - 20124 Milano, Italy.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.RequestHandle;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.client.transport.providers.CookieHelper;
import com.lightstreamer.client.transport.providers.HttpProvider;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.GlobalProperties;
import com.lightstreamer.util.threads.ThreadShutdownHook;

public class OIOHttpProvider implements HttpProvider {

    private static final String ua;

    static {
        if (LightstreamerClient.LIB_NAME.contains("placeholder")) {
            ua = "Lightstreamer dev client OIO";
        } else {
            ua = LightstreamerClient.LIB_NAME + " " + LightstreamerClient.LIB_VERSION;
        }
        /*
         * Inhibit the automatic retransmission of POST requests because of IO exceptions or malformed responses
         * (see http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6382788).
         * The retransmissions must always be done by the client according to the TLCP protocol specification.
         */
        System.setProperty("sun.net.http.retryPost", "false");
    }

    protected final Logger log = LogManager.getLogger(Constants.TRANSPORT_LOG);

    private SessionThread sessionThread;

    private OIOThreadPoolExecutorWrapper executor;
    
    private static final AtomicInteger objectIdCounter = new AtomicInteger();
    private final int objectId;
    
    public OIOHttpProvider(SessionThread thread) {
        this.sessionThread = thread;
        this.objectId = objectIdCounter.incrementAndGet();
    }

    @Override
    public RequestHandle createConnection(Protocol protocol, LightstreamerRequest request, HttpRequestListener httpListener, Map<String, String> extraHeaders, Proxy proxy, long tcpConnectTimeout, long tcpReadTimeout) throws SSLException {

        String address =
            request.getTargetServer() + "lightstreamer/" + request.getRequestName() + ".txt"+"?LS_protocol=" + Constants.TLCP_VERSION;
        final URL url;
        try {
            url = new URL(address);
        } catch (MalformedURLException e) {
            log.fatal("Unexpectedly invalid URI: " + address, e);
            throw new IllegalArgumentException(e);
        }

        Connection conn =
            new Connection(address, url, request.getTransportAwareQueryString(null, true), httpListener, extraHeaders,
                proxy, tcpConnectTimeout, tcpReadTimeout);

        executor = OIOThreadPoolExecutorWrapper.get();
        executor.execute(conn);
        return conn;
    }

    @Override
    public ThreadShutdownHook getShutdownHook() {
        return new ThreadShutdownHook() {

            @Override
            public void onShutdown() {
                OIOThreadPoolExecutorWrapper.close();
            }
        };
    }

    private class Connection implements RequestHandle, Runnable {

        private URL url;
        private String address;
        private String params;
        private RequestListener listener;

        AtomicBoolean closed = new AtomicBoolean(false);
        private Map<String, String> extraHeaders;
        private Proxy proxy;
        private long tcpReadTimeout;
        private long tcpConnectTimeout;

        public Connection(String address,
                          URL url,
                          String params,
                          RequestListener networkListener,
                          Map<String, String> extraHeaders,
                          Proxy proxy,
                          long tcpConnectTimeout,
                          long tcpReadTimeout) {
            this.address = address;
            this.url = url;
            this.params = params;
            this.listener = networkListener;
            this.extraHeaders = extraHeaders;
            this.proxy = proxy;

            this.tcpConnectTimeout = tcpConnectTimeout;
            this.tcpReadTimeout = tcpReadTimeout;
        }

        @Override
        public void run() {
            if (log.isDebugEnabled()) {
                log.debug("OIO transport sending (oid=" + objectId + "): " + url.getPath() + "\n" + params);
            }
            OIOThreadPoolExecutorWrapper.release(executor);
            
            HttpURLConnection connection = null;
            OutputStream out = null;
            BufferedReader in = null;

            boolean completed = false;
            try {
                /* CANBLOCK */
                if (proxy != null) {
                    OIOProxy oioProxy = new OIOProxy(proxy);
                    connection = (HttpURLConnection) url.openConnection(oioProxy.getProxy());
                } else {
                    connection = (HttpURLConnection) url.openConnection();
                }
                if (connection == null) {
                    log.debug("Failed connection (oid=" + objectId + ") to " + address);
                    throw new IllegalStateException();
                }

                if (closed.get()) {
                    return;
                }

                listener.onOpen();
                
                if (connection instanceof HttpsURLConnection && GlobalProperties.INSTANCE.getTrustManagerFactory() != null) {
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, GlobalProperties.INSTANCE.getTrustManagerFactory().getTrustManagers(), null);
                    SSLSocketFactory sslFactory = ctx.getSocketFactory();
                    ((HttpsURLConnection) connection).setSSLSocketFactory(sslFactory);
                }

                connection.setRequestMethod("POST");
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setAllowUserInteraction(false);
                connection.setInstanceFollowRedirects(true);

                if (tcpReadTimeout > 0) {
                    connection.setReadTimeout(intify(tcpConnectTimeout + tcpReadTimeout));
                }
                if (tcpConnectTimeout > 0) {
                    connection.setConnectTimeout(intify(tcpConnectTimeout));
                }

                connection.setRequestProperty("User-Agent", ua);
                connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                if (extraHeaders != null) {
                    for (Entry<String, String> header : extraHeaders.entrySet()) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }

                URI uri = null;
                try {
                    uri = url.toURI();
                } catch (URISyntaxException e) {
                    log.error("Problem creating the cookie header", e);
                }
                
                /* add cookies to the request */
                if (CookieHelper.isCookieHandlerLocal()) {
                    // NB if the user has installed a default cookie manager, the Java standard library
                    // automatically and transparently manages cookies.
                    // But if there is no default manager, we relies on the manual management provided by CookieHelper
                    if (uri != null) {
                        String cookies = CookieHelper.getCookieHeader(uri);
                        if (cookies != null && cookies.length() > 0) {
                            connection.setRequestProperty("cookie", cookies);
                        }
                    }
                }
                
                out = connection.getOutputStream();

                if (params != null) {
//                    if (log.isDebugEnabled()) {
//                        log.debug("Posting data: " + params);
//                    }

                    byte[] encoded = params.getBytes(StandardCharsets.UTF_8);
                    /* CANBLOCK */
                    out.write(encoded, 0, encoded.length);

                    if (closed.get()) {
                        return;
                    }
                }

                /* CANBLOCK */
                out.flush();

                if (closed.get()) {
                    return;
                }

                /* CANBLOCK */
                out.close();
                out = null;

                if (closed.get()) {
                    return;
                }

                int respCode = connection.getResponseCode();
                if (respCode < 200) {
                    // 100 family, not expected
                    listener.onBroken();
                    return;
                } else if (respCode < 300) {
                    // 200 family: all is good
                } else if (respCode < 400) {
                    // 300 family: automatically handled by java, should never
                    // pass from here
                    listener.onBroken();
                    return;
                } else {
                    // 400 - 500 families: problems
                    listener.onBroken();
                    return;
                }
                
                /* save cookies from the response */
                if (CookieHelper.isCookieHandlerLocal()) {
                    // NB if the user has installed a default cookie manager, the Java standard library
                    // automatically and transparently manages cookies.
                    // But if there is no default manager, we relies on the manual management provided by CookieHelper
                    if (uri != null) {
                        String headerName = null;
                        for (int i = 1; (headerName = connection.getHeaderFieldKey(i)) != null; i++) {
                            if (headerName.equalsIgnoreCase("Set-Cookie")) {
                                String cookie = connection.getHeaderField(i);
                                CookieHelper.saveCookies(uri, cookie);
                            }
                        }
                    }
                }

                in = new BufferedReader(new InputStreamReader(connection.getInputStream()), 8192);

                /* CANBLOCK */
                String message;
                boolean stopped = false;
                while ((message = in.readLine()) != null) {
                    if (!closed.get()) {
                        if (log.isDebugEnabled()) {
                            log.debug("OIO transport receiving (oid=" + objectId + "):\n" + message);
                        }
                        listener.onMessage(message); // not very smart
                                                              // XXX
                    } else {
                        stopped = true;
                        break;
                    }
                }

                completed = !stopped;

            } catch (IllegalStateException e) {
                // not broken, just never opened
                log.warn("OIO transport problem (oid=" + objectId + "): " + url.getPath() + "\n" + params, e);

            } catch (Exception e) {
                log.error("OIO transport error (oid=" + objectId + "): " + url.getPath() + "\n" + params, e);
                if (!closed.get()) {
                    listener.onBroken();
                }

            } finally {
                if (!completed && connection != null) {
                    // means the connection was willingly closed
                    // while it was still receiving stuff
                    log.info("Force socket close");
                    connection.disconnect();
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                    }
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }

                if (!closed.get()) {
                    listener.onClosed();
                }
                log.debug(listener + " has finished");
            }
        }

        @Override
        public void close(boolean forceConnectionClose) {
            closed.set(true);
        }

    }

    private static int intify(long longValue) {
        // NOTE: can't be negative
        if (longValue > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int) longValue;
    }

}
