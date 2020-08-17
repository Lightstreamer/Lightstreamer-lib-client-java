package com.lightstreamer.util;

import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;

public class Assertions {
    
    private static final Logger log = LogManager.getLogger("ASSERT");

    public static boolean isSessionThread() {
        if (! Thread.currentThread().getName().startsWith("Session Thread")) {
            //log.error("The method must be called by Session Thread. Instead the caller is " + Thread.currentThread());
            return false;
        }
        return true;
    }
    
    public static boolean isEventThread() {
        if (! Thread.currentThread().getName().startsWith("Events Thread")) {
            //log.error("The method must be called by Event Thread. Instead the caller is " + Thread.currentThread());
            return false;
        }
        return true;
    }
    
    public static boolean isNettyThread() {
        if (! Thread.currentThread().getName().startsWith("Netty Thread")) {
            //log.error("The method must be called by Netty Thread. Instead the caller is " + Thread.currentThread());
            return false;
        }
        return true;
    }
    
    /**
     * Conditional operator.
     */
    public static boolean implies(boolean a, boolean b) {
        return ! a || b;
    }
    
    /**
     * Biconditional operator.
     */
    public static boolean iff(boolean a, boolean b) {
        return a == b;
    }
}
