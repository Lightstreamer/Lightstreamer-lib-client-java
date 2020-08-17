package com.lightstreamer.client.session;

/**
 * A server session.
 * <p>
 * <b>NB</b>
 * The class {@link Session}, notwithstanding the name, does not represent a server session because in general it has a 
 * shorter life span than the corresponding server session. Rather it represents the current stream connection 
 * (a server session is made of a sequence of stream connections).
 * 
 * 
 * @since January 2017
 */
public class ServerSession {
    private State state;
    private Session streamConnection;
    
    /**
     * Builds a server session using the specified stream connection.
     */
    public ServerSession(Session initialStreamConnection) {
        this.state = State.OPEN;
        this.streamConnection = initialStreamConnection;
    }
    
    /**
     * Changes the current stream connection.
     */
    public void setNewStreamConnection(Session newStreamConnection) {
        this.streamConnection = newStreamConnection;
    }
    
    /**
     * Returns whether the current stream connection is the same as the specified connection.
     */
    public boolean isSameStreamConnection(Session tutorStreamConnection) {
        return streamConnection == tutorStreamConnection;
    }
    
    /**
     * Returns whether the underlying stream connection is using a HTTP transport.
     */
    public boolean isTransportHttp() {
        return streamConnection instanceof SessionHTTP;
    }
    
    /**
     * Returns whether the underlying stream connection is using a WebSocket transport.
     */
    public boolean isTransportWS() {
        return streamConnection instanceof SessionWS;
    }
    
    public boolean isOpen() {
        return state.equals(State.OPEN);
    }
    
    public boolean isClosed() {
        return state.equals(State.CLOSED);
    }
    
    public void close() {
        state = State.CLOSED;
    }

    private static enum State {
        OPEN, CLOSED
    }
}
