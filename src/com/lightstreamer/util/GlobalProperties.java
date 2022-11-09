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
