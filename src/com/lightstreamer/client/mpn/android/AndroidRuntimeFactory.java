package com.lightstreamer.client.mpn.android;

import javax.annotation.concurrent.GuardedBy;

/**
 * Singleton factory returning the Android runtime.
 * 
 * 
 * @since July 2017
 */
public class AndroidRuntimeFactory {
    
    @GuardedBy("AndroidRuntimeFactory.class")
    private static AndroidRuntime runtime;
    
    /**
     * Sets a runtime if it is not already available. Otherwise keeps the current runtime.
     */
    public static synchronized void initRuntime(AndroidRuntime rt) {
        if (runtime == null) {
            runtime = rt;
        }
    }

    /**
     * Returns the Android runtime.
     */
    public static synchronized AndroidRuntime getRuntime() {
        return runtime;
    }
   
    /**
     * Test only.<br>
     * The runtime must be set before creating the MPN devices.
     */
    public static synchronized void setRuntime(AndroidRuntime rt) {
        runtime = rt;
    }
}
