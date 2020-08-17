package com.lightstreamer.client.transport;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.protocol.Protocol;
import com.lightstreamer.client.protocol.TextProtocol.StreamListener;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.providers.CookieHelper;
import com.lightstreamer.client.transport.providers.TransportFactory;
import com.lightstreamer.client.transport.providers.WebSocketProvider;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Assertions;
import com.lightstreamer.util.threads.ThreadShutdownHook;

/**
 * A WebSocket transport implemented using {@link WebSocketProvider}.
 * <br>
 * Its main responsibility are:
 * <ol>
 * <li>exposing a method to write frames into the connection (see {@link #sendRequest})</li>
 * <li>notifying the listeners of the events coming from the connection</li>
 * <li>assuring that the notifications are executed by the SessionThread.</li>
 * </ol>
 * 
 * 
 * @since October 2016
 */
public class WebSocket implements Transport {
    
    private static final Logger log = LogManager.getLogger(Constants.TRANSPORT_LOG);
    
    private final SessionThread sessionThread;
    private final InternalConnectionOptions options;
    private final WebSocketProvider wsClient;
    private final MySessionRequestListener sessionListener;
    /**
     * When not null, the requests may omit the parameter LS_session because it is implicitly equal to this value.<br>
     * This value is always equal to the LS_session parameter of the last sent bind_session request.
     */
    private String defaultSessionId;
    
    public WebSocket(SessionThread sessionThread, InternalConnectionOptions options,
            String serverAddress, StreamListener streamListener, ConnectionListener connListener) {
        this.sessionThread = sessionThread;
        this.options = options;
        if (TransportFactory.getDefaultWebSocketFactory() == null) {
            /* NB
             * This is a temporary hack. If the WebSocket support is not available, the transport uses a void implementation
             * which just ignores the requests.
             * The goal is to emulate the behavior of the old (non TLCP) Android compact client.
             * That client doesn't support WebSocket, so when a user forces WebSocket transport
             * the client simply ignores the requests.
             * In the future we must address this question and work out a more user-friendly API behavior. 
             */
            this.wsClient = new DummyWebSocketClient();
        } else {            
            this.wsClient = TransportFactory.getDefaultWebSocketFactory().getInstance(sessionThread);
        }
        this.sessionListener = new MySessionRequestListener(sessionThread, streamListener, connListener);
        open(serverAddress, streamListener, connListener);
        if (log.isDebugEnabled()) {
            log.debug("WebSocket transport: " + sessionListener.state);
        }
    }
    
    /**
     * Opens a WebSocket connection.
     * 
     * @param serverAddress target address
     * @param streamListener is exposed to the following connection events: opening, closing, reading a message, catching an error. 
     * For each event the corresponding listener method is executed on the SessionThread. 
     * @param connListener is only exposed to the event opening connection. The listener method is executed on the SessionThread.
     */
    private void open(String serverAddress, StreamListener streamListener, ConnectionListener connListener) {
        assert Assertions.isSessionThread();
        assert sessionListener.state == InternalState.NOT_CONNECTED;
        sessionThread.registerWebSocketShutdownHook(wsClient.getThreadShutdownHook());
        URI uri;
        try {
            uri = new URI(serverAddress + "lightstreamer");
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e); // should never happen
        }
        String cookies = CookieHelper.getCookieHeader(uri);
        wsClient.connect(uri.toString(), 
                sessionListener, 
                /* if the flag is true, the extra-headers are sent only for create_session request (which is always in HTTP) */
                options.isHttpExtraHeadersOnSessionCreationOnly() ? null : options.getHttpExtraHeaders(), 
                cookies, 
                options.getProxy());
        sessionListener.state = InternalState.CONNECTING;
        if (log.isDebugEnabled()) {
            log.debug("WebSocket transport: " + sessionListener.state);
        }
    }
    
    @Override
    public RequestHandle sendRequest(Protocol protocol, final LightstreamerRequest request, final RequestListener listener, 
            final Map<String, String> extraHeaders, final Proxy proxy,
            final long tcpConnectTimeout, final long tcpReadTimeout) {
        /* the parameters protocol, extraHeaders, proxy, tcpConnectTimeout and tcpReadTimeout 
         * have no meaning for WebSocket connections */
        assert extraHeaders == null && proxy == null && tcpConnectTimeout == 0 && tcpReadTimeout == 0;
        assert Assertions.isSessionThread();
        assert sessionListener.state == InternalState.CONNECTED : sessionListener.state;
        String frame = request.getRequestName() + "\r\n" + request.getTransportAwareQueryString(defaultSessionId, false);
        wsClient.send(frame, listener);
        return new RequestHandle() {
            @Override
            public void close(boolean forceConnectionClose) {
                /* NB
                 * This method must not be used. 
                 * In order to close the connection, use WebSocket.close() instead.
                 */
                assert false;
            }
        };
    }
    
    /**
     * Closes the connection.
     */
    public void close() {
        assert Assertions.isSessionThread();
        sessionListener.close();
        wsClient.disconnect();
    }

    public InternalState getState() {
        return sessionListener.state;
    }
    
    public void setDefaultSessionId(String sessionId) {
        defaultSessionId = sessionId;
    }
    
    /**
     * Forwards the messages coming from the data stream to the connection listeners.
     * <p>
     * NB All the methods must be called by SessionThread in order to fulfill the contract of {@link WebSocket#open}.
     */
    private static class MySessionRequestListener implements SessionRequestListener {
        
        private final SessionThread sessionThread;
        private final StreamListener streamListener;
        private final ConnectionListener connectionListener;
        /**
         * <b>NB</b> state must be volatile because it is read by methods of {@link MySessionRequestListener} 
         * which are NOT called by Session Thread. 
         */
        volatile InternalState state = InternalState.NOT_CONNECTED;

        MySessionRequestListener(SessionThread sessionThread, StreamListener streamListener, ConnectionListener connListener) {
            this.sessionThread = sessionThread;
            this.streamListener = streamListener;
            this.connectionListener = connListener;
        }
        
        /*
         * Note 1
         * Methods below are called by WebSocketProvider internal threads.
         * Their bodies must be delegated to SessionThread.
         * 
         * Note 2
         * It can happen that the socket is disconnected but the listeners, notified by Netty,
         * still receive events (for example when the socket is closed because the control link has changed). 
         * These events must be ignored.
         */

        @Override
        public void onOpen() {
            sessionThread.queue(new Runnable() {
                @Override
                public void run() {
                    if (state.equals(InternalState.DISCONNECTED)) {
                        log.warn("onOpen event discarded");
                        return;
                    }
                    state = InternalState.CONNECTED;
                    if (log.isDebugEnabled()) {
                        log.debug("WebSocket transport: " + state);
                    }
                    connectionListener.onOpen();
                }
            });
        }

        @Override
        public void onMessage(final String frame) {
            sessionThread.queue(new Runnable() {
                public void run() {
                    if (state.equals(InternalState.DISCONNECTED)) {
                        log.warn("onMessage event discarded: " + frame);
                        return;
                    }
                    streamListener.onMessage(frame);
                }
            });            
        }

        @Override
        public void onClosed() {
            sessionThread.queue(new Runnable() {
                public void run() {
                    if (state.equals(InternalState.DISCONNECTED)) {
                        log.warn("onClosed event discarded");
                        return;
                    }
                    streamListener.onClosed();
                }
            });
        }

        @Override
        public void onBroken() {
            sessionThread.queue(new Runnable() {
                public void run() {
                    if (state.equals(InternalState.DISCONNECTED)) {
                        log.warn("onBroken event discarded");
                        return;
                    }
                    state = InternalState.BROKEN;
                    connectionListener.onBroken();
                    streamListener.onBrokenWS();
                }
            });
        }
        
        void close() {
            state = InternalState.DISCONNECTED;
            if (streamListener != null) {
                streamListener.disable();
                streamListener.onClosed();
            }
            if (log.isDebugEnabled()) {
                log.debug("WebSocket transport: " + state);
            }
        }
    }
    
    public enum InternalState {
        /**
         * Initial state.
         */
        NOT_CONNECTED, 
        /**
         * State after calling {@link WebSocket#open(String, StreamListener, ConnectionListener)}.
         */
        CONNECTING, 
        /**
         * State after the method {@link WebSocketRequestListener#onOpen()} is called.
         */
        CONNECTED, 
        /**
         * State after calling {@link WebSocket#close()}. In this state, the listeners are disabled.
         */
        DISCONNECTED,
        /**
         * Transport can't connect to the server.
         */
        BROKEN}

    /**
     * Callback interface to capture connection opening event.
     */
    public interface ConnectionListener {
        /**
         * Fired when the connection is established.
         */
        void onOpen();
        /**
         * Fired when the connection can't be established.
         */
        void onBroken();
    }
    
    private static class DummyWebSocketClient implements WebSocketProvider {
        @Override
        public void connect(String address, SessionRequestListener networkListener, Map<String, String> extraHeaders, String cookies, Proxy proxy) {}

        @Override
        public void disconnect() {}
        
        @Override
        public void send(String message, RequestListener listener) {}

        @Override
        public ThreadShutdownHook getThreadShutdownHook() {
            return new ThreadShutdownHook() {
                public void onShutdown() {}
            };
        }
    }
    
    /*
     * --------------------------------------------------------------------
     * Other stuff
     * --------------------------------------------------------------------
     */
    
    private static final AtomicBoolean disabled = new AtomicBoolean(false);
    
    public static boolean isDisabled() {
        return disabled.get();
    }
    
    public static void restore() {
        disabled.set(false);
    }
    
    public static void disable() {
        disabled.set(true);
    }
}
