package com.lightstreamer.client.transport.providers;

import java.util.Map;

import com.lightstreamer.client.Proxy;
import com.lightstreamer.client.transport.RequestListener;
import com.lightstreamer.client.transport.SessionRequestListener;
import com.lightstreamer.util.threads.ThreadShutdownHook;

/**
 * Interface used to decouple the application classes from a specific WebSocket implementation (for example Netty {@link WebSocketClient}).
 * Instances of this type are obtained through the factory {@link TransportFactory#getDefaultWebSocketFactory()}.
 * 
 * 
 * @since November 2016
 */
public interface WebSocketProvider {
    
    /**
     * Opens a WebSocket connection.
     * @param address host address
     * @param networkListener listens to connection events (opening, closing, message receiving, error)
     * @param extraHeaders headers to be added during WebSocket handshake
     * @param cookies cookies to be added during WebSocket handshake
     * @param proxy if not null, the client connects to the proxy and the proxy forwards the messages to the host 
     */
    void connect(String address, SessionRequestListener networkListener, Map<String, String> extraHeaders, String cookies, Proxy proxy);

    /**
     * Sends a message.
     * <p>
     * <b>NB</b> When the message has been successfully written on WebSocket,
     * it is mandatory to notify the method {@link RequestListener#onOpen()}.
     */
    void send(String message, RequestListener listener);

    /**
     * Closes the connection.
     */
    void disconnect();

    /**
     * Returns a callback to free the resources (threads, sockets...) allocated by the provider.
     */
    ThreadShutdownHook getThreadShutdownHook();
}