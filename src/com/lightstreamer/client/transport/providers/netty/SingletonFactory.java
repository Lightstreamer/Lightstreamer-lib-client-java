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
