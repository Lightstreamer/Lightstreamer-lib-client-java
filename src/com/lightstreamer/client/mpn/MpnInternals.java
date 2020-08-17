package com.lightstreamer.client.mpn;

/**
 * Utility methods to allow classes outside of the MPN package to call non-public methods of MPN classes.
 */
public class MpnInternals {
    
    public static String getItemsDescriptor(MpnSubscription sub) {
        return sub.getItemsDescriptor();
    }
    
    public static String getFieldsDescriptor(MpnSubscription sub) {
        return sub.getFieldsDescriptor();
    }
    
    public static void subscribe(MpnSubscription sub) {
        sub.onSubscribe();
    }
    
    public static void unsubscribe(MpnSubscription sub) {
        sub.throwErrorIfInactiveOrUnsubscribing();
        sub.onUnsubscribe();
    }
}
