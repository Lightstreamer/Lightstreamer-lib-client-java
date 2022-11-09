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
