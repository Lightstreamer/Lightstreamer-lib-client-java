package com.lightstreamer.client.protocol;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.protocol.TextProtocol.StreamListener;
import com.lightstreamer.client.requests.BindSessionRequest;
import com.lightstreamer.client.requests.ControlRequest;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.SessionRequest;
import com.lightstreamer.client.requests.LightstreamerRequest;
import com.lightstreamer.client.requests.MessageRequest;
import com.lightstreamer.client.requests.NumberedRequest;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.client.transport.RequestHandle;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.client.transport.WebSocket;
import com.lightstreamer.client.transport.WebSocket.ConnectionListener;
import com.lightstreamer.client.transport.WebSocket.InternalState;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Assertions;
import com.lightstreamer.util.ListenableFuture;

/**
 * The manager forwards the requests to the WebSocket transport ensuring that if the underlying connection is not ready,
 * the requests are buffered and sent later.
 * <p>
 * <b>Note 1</b>
 * Method {@link #openSocket(String, StreamListener)} is used when the flag isEarlyWSOpenEnabled is set. If the method is not called explicitly,
 * method {@link #bindSession(SessionRequest, RequestListener, long, long, ListenableFuture)} will call it.
 * <p>
 * <b>Note 2</b>
 * If method {@link #openSocket(String, StreamListener)} is called twice in a row (this can happen if the server sends a control-link), 
 * the final effect is to close the old socket and to open a new one.
 * 
 * 
 * @since October 2016
 */
public class WebSocketRequestManager implements RequestManager {
    
    private final Logger log = LogManager.getLogger(Constants.REQUESTS_LOG);
    private final Logger sessionLog = LogManager.getLogger(Constants.SESSION_LOG);
    
    private WebSocket wsTransport;
    private Protocol protocol;
    private final SessionThread sessionThread;
    private final InternalConnectionOptions options;
    private final ArrayDeque<PendingRequest> controlRequestQueue = new ArrayDeque<>();
    private PendingBind bindRequest;
    /**
     * Request that the manager has sent but it has not been written on WebSocket.
     * Must be cleared when {@link RequestListener#onOpen()} is called 
     * (we assume that WebSocket is reliable).
     */
    private PendingRequest ongoingRequest;
    /**
     * Maps the LS_reqId of a request to the listener of the request.
     */
    private final Map<Long, RequestListener> pendingRequestMap = new HashMap<>();
    private ListenableFuture openWsFuture;
    
    public WebSocketRequestManager(SessionThread sessionThread, Protocol protocol, InternalConnectionOptions options) {
        assert Assertions.isSessionThread();
        this.options = options;
        this.sessionThread = sessionThread;
        this.protocol = protocol;
    }
    
    /**
     * Opens a WebSocket connection without binding a session (see the flag isEarlyWSOpenEnabled). 
     * If a connection is already open, the connection is closed and a new connection is opened.
     * @param serverAddress server address
     * @param streamListener stream connection listener
     */
    public ListenableFuture openWS(Protocol protocol, String serverAddress, StreamListener streamListener) {
        assert Assertions.isSessionThread();
        sessionLog.debug("WS connection: opening");
        if (wsTransport != null) {
            // close old connection
            wsTransport.close();
        }
        wsTransport = new WebSocket(sessionThread, options, serverAddress, streamListener, new MyConnectionListener());
        assert wsTransport.getState().equals(InternalState.CONNECTING);
        openWsFuture = new ListenableFuture();
        /* abort connection if opening takes too long */
        final WebSocket _wsTransport = wsTransport;
        final ListenableFuture _openWsFuture = openWsFuture;
        if (sessionLog.isDebugEnabled()) {        
            sessionLog.debug("Status timeout in " + options.getCurrentConnectTimeout() + " [currentConnectTimeoutWS]");
        }
        sessionThread.schedule(new Runnable() {
            @Override
            public void run() {
                if (log.isDebugEnabled()) {        
                    log.debug("Timeout event [currentConnectTimeoutWS]");
                }
                if (_wsTransport.getState().equals(InternalState.CONNECTING)) {
                    sessionLog.debug("WS connection: aborted");
                    _openWsFuture.reject();
                    _wsTransport.close();
                    options.increaseConnectTimeout();
                }
            }
        }, options.getCurrentConnectTimeout());
        return openWsFuture;
    }

    /**
     * {@inheritDoc}
     * If the socket is not open, calls {@link #openSocket(String, StreamListener)}.
     */
    @Override
    public RequestHandle bindSession(BindSessionRequest request, StreamListener reqListener, long tcpConnectTimeout, long tcpReadTimeout, ListenableFuture bindFuture) {
        assert Assertions.isSessionThread();
        if (wsTransport == null) {
            // no transport: this case can happen when transport is polling
            bindRequest = new PendingBind(request, reqListener, bindFuture);
            openWS(protocol, request.getTargetServer(), reqListener);

        } else {
            // there is a transport, so openSocket was already called: the state is CONNECTED or CONNECTING 
            InternalState state = wsTransport.getState();
            switch (state) {
            case CONNECTED:
                sendBindRequest(request, reqListener, bindFuture);
                break;

            case CONNECTING:
                // buffer the request, which will be flushed when the client state is CONNECTED
                assert bindRequest == null;
                bindRequest = new PendingBind(request, reqListener, bindFuture);
                break;
                
            case BROKEN:
                // discard bind request: must be sent in HTTP
                break;

            default:
                assert false : state;
            }
        }
        // this request handle close the stream connection
        return new RequestHandle() {
            @Override
            public void close(boolean forceConnectionClose) {
                WebSocketRequestManager.this.close(false);
            }
        };
    }

    @Override
    public void addRequest(LightstreamerRequest request, RequestTutor tutor, RequestListener reqListener) {
        assert Assertions.isSessionThread();
        assert request instanceof ControlRequest || request instanceof MessageRequest || request instanceof ReverseHeartbeatRequest;
        if (request instanceof NumberedRequest) {
            /*
             * for numbered requests (i.e. having a LS_reqId) the client expects a REQOK/REQERR notification from the server 
             */
            NumberedRequest numberedReq = (NumberedRequest) request;
            assert ! pendingRequestMap.containsKey(numberedReq.getRequestId());
            pendingRequestMap.put(numberedReq.getRequestId(), reqListener);
        }
        if (wsTransport == null) {
            // no transport: this case can happen for example when the flag isEarlyWSOpenEnabled is off.
            // buffer the request and await the binding of the session
            controlRequestQueue.addLast(new PendingRequest(request, reqListener, tutor));
            
        } else {
            // there is a transport, so openSocket was already called: the state is CONNECTED or CONNECTING 
            switch (wsTransport.getState()) {
            case CONNECTED:
                sendControlRequest(request, reqListener, tutor);
                break;
                
            case CONNECTING:
                // buffer the requests, which will be flushed when the client state is CONNECTED
                controlRequestQueue.addLast(new PendingRequest(request, reqListener, tutor));
                break;
                
            default:
                assert false;
            }
        }
    }
    
    private void sendControlRequest(LightstreamerRequest request, final RequestListener reqListener, RequestTutor tutor) {
        assert Assertions.isSessionThread();
        ongoingRequest = new PendingRequest(request, reqListener, tutor);
        wsTransport.sendRequest(protocol, request, new ListenerWrapper(reqListener) {
            @Override
            public void doOpen() {
                /* the request has been sent: clear the field */
                ongoingRequest = null;
            }
        }, null, null, 0, 0);
    }
    
    private void sendBindRequest(LightstreamerRequest request, RequestListener reqListener, ListenableFuture bindFuture) {
        assert Assertions.isSessionThread();
        wsTransport.sendRequest(protocol, request, new ListenerWrapper(reqListener), null, null, 0, 0);
        bindFuture.fulfill();
    }
    
    @Override
    public void close(boolean waitPending) {
        assert Assertions.isSessionThread();
        log.debug("WebSocket request manager closing");
        if (wsTransport != null) {            
            wsTransport.close();
            wsTransport = null;
        }
    }

    @Override
    public void setRequestLimit(long limitNum) {
        assert Assertions.isSessionThread();
        /*
         * The limit is important when a manager sends the requests in batch to limit the dimension of the batch.
         * Since this manager sends requests one by one, the limit is useless.
         * Note that if a single request is bigger than the limit, the manager
         * sends it anyway but the server will refuse it.
         */
    }

    @Override
    public void copyTo(ControlRequestHandler newHandler) {
        assert Assertions.isSessionThread();
        if (ongoingRequest != null) {
            newHandler.addRequest(ongoingRequest.request, ongoingRequest.tutor, ongoingRequest.reqListener);
        }
        for (PendingRequest pendingRequest : controlRequestQueue) {
            newHandler.addRequest(pendingRequest.request, pendingRequest.tutor, pendingRequest.reqListener);
        }
        /* clear memory */
        ongoingRequest = null;
        controlRequestQueue.clear();
    }
    
    /**
     * Sets the default session id of a WebSocket connection.
     * The default id is the id returned in the CONOK response of a bind_session.
     * It lasts until the receiving of a LOOP or END message.
     */
    public void setDefaultSessionId(String sessionId) {
        assert wsTransport != null;
        wsTransport.setDefaultSessionId(sessionId);
    }
    
    /**
     * Finds the listener associated with the request.
     * If found, removes it from the list of pending requests.
     */
    public RequestListener getAndRemoveRequestListener(long reqId) {
        RequestListener reqListener = pendingRequestMap.remove(reqId);
        return reqListener;
    }
    
    /**
     * Sends the requests (when the state is CONNECTED) which were buffered because the connection wasn't ready.
     */
    private class MyConnectionListener implements ConnectionListener {
        @Override
        public void onOpen() {
            assert Assertions.isSessionThread(); // NB the callback caller must assure the execution on SessionThread
            assert wsTransport.getState().equals(InternalState.CONNECTED);
            sessionLog.debug("WS connection: open");
            openWsFuture.fulfill();
            /* send bind_session */
            if (bindRequest != null) {
                // bind request has precedence over control requests
                sendBindRequest(bindRequest.request, bindRequest.reqListener, bindRequest.bindFuture);
            }
            /* send control requests */
            for (PendingRequest controlRequest : controlRequestQueue) {
                sendControlRequest(controlRequest.request, controlRequest.reqListener, controlRequest.tutor);
            }
            /* release memory */
            bindRequest = null;
            controlRequestQueue.clear();
        }

        @Override
        public void onBroken() {
            assert Assertions.isSessionThread(); // NB the callback caller must assure the execution on SessionThread
            sessionLog.debug("WS connection: broken");
            openWsFuture.reject();
        }
    }

    private static class PendingRequest {
        final LightstreamerRequest request;
        final RequestListener reqListener;
        final RequestTutor tutor;

        public PendingRequest(LightstreamerRequest request, RequestListener reqListener, RequestTutor tutor) {
            this.request = request;
            this.reqListener = reqListener;
            this.tutor = tutor;
        } 
    }
    
    private static class PendingBind extends PendingRequest {
        final ListenableFuture bindFuture;
        
        public PendingBind(LightstreamerRequest request, RequestListener reqListener, ListenableFuture bindFuture) {
            super(request, reqListener, null /* not used */);
            this.bindFuture = bindFuture;
        }
    }
    
    /**
     * A wrapper assuring that the method {@link RequestListener#onOpen()} is executed
     * in the SessionThread.
     */
    private class ListenerWrapper implements RequestListener {
        private final RequestListener reqListener;
        
        public ListenerWrapper(RequestListener listener) {
            reqListener = listener;
        }
        
        /**
         * Extra-operations to perform before executing {@link RequestListener#onOpen()}.
         */
        public void doOpen() {
            
        }
        
        @Override
        public final void onOpen() {
            sessionThread.queue(new Runnable() {
                @Override
                public void run() {
                    doOpen();
                    reqListener.onOpen(); // onOpen fires the retransmission timeout
                }
            });
        }
        
        @Override
        public final void onMessage(String message) {
            reqListener.onMessage(message);
        }
        
        @Override
        public final void onClosed() {
            reqListener.onClosed();
        }
        
        @Override
        public final void onBroken() {
            reqListener.onBroken();
        }
    }
}
