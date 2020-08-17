package com.lightstreamer.client;

import com.lightstreamer.util.Descriptor;

/**
 * Utility methods to access non-public methods/fields from a package different from the containing package.
 * The methods/fields must remain non-public because the containing classes are exported as JavaDoc
 * and the final user mustn't see them.
 */
public /* @exclude */ class Internals {

    public static Descriptor getItemDescriptor(Subscription sub) {
        return sub.itemDescriptor;
    }
    
    public static Descriptor getFieldDescriptor(Subscription sub) {
        return sub.fieldDescriptor;
    }
    
    public static int getRequestedBufferSize(Subscription sub) {
        return sub.requestedBufferSize;
    }
    
    public static double getRequestedMaxFrequency(Subscription sub) {
        return sub.requestedMaxFrequency;
    }
}
