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
