package com.lightstreamer.util;

import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.TrustManagerFactory;

/**
 * Singleton class storing global properties affecting the behavior of the library.
 * 
 * 
 * @since December 2017
 */
@ThreadSafe
public class GlobalProperties {
    
    public static final GlobalProperties INSTANCE = new GlobalProperties();
    
    private TrustManagerFactory trustManagerFactory;
    
    private GlobalProperties() {}

    public synchronized TrustManagerFactory getTrustManagerFactory() {
        return trustManagerFactory;
    }

    public synchronized void setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        this.trustManagerFactory = trustManagerFactory;
    }
}
