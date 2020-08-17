package com.lightstreamer.client.transport.providers.netty;

import com.lightstreamer.client.transport.providers.netty.pool.WebSocketPoolManager;

/**
 * Factory returning objects which must be singletons.
 * 
 * 
 * @since August 2017
 */
public class SingletonFactory {
    
    public static final SingletonFactory instance = new SingletonFactory();
    
    private final HttpPoolManager httpPool;
    private final WebSocketPoolManager wsPool;
    
    private SingletonFactory() {
        httpPool = new HttpPoolManager();
        wsPool = new WebSocketPoolManager(httpPool);
    }
    
    /**
     * Returns the global HTTP pool.
     */
    public HttpPoolManager getHttpPool() {
        return httpPool;
    }

    /**
     * Returns the global WebSocket pool.
     */
    public WebSocketPoolManager getWsPool() {
        return wsPool;
    }
    
    /**
     * Releases the resources acquired by the singletons.
     */
    public void close() {
        /*
         * NB WebSocket pool depends on HTTP pool, so it must be closed firstly
         */
        wsPool.close();
        httpPool.close();
    }
}
