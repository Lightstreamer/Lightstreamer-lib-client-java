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
