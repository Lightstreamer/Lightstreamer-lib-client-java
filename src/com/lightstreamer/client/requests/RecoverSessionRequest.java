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


package com.lightstreamer.client.requests;

import com.lightstreamer.client.session.InternalConnectionOptions;

/**
 * A recovery request is a special type of bind_session request with the additional LS_recovery_from parameter.
 * <p>
 * The class was adapted from {@link CreateSessionRequest}.
 */
public class RecoverSessionRequest extends SessionRequest {

    public RecoverSessionRequest(
            String targetServer, 
            String session, 
            String cause, 
            InternalConnectionOptions options, 
            long delay, 
            long sessionRecoveryProg) {
        
        super(true,delay);

        this.setServer(targetServer);

        this.addParameter("LS_polling", "true");

        if (cause != null) {
            this.addParameter("LS_cause", cause);
        }

        long requestedPollingInterval = delay > 0 ? delay : 0; // NB delay can be negative since it is computed by SlowingHandler
        long requestedIdleTimeout = 0;
        this.addParameter("LS_polling_millis", requestedPollingInterval);
        this.addParameter("LS_idle_millis", requestedIdleTimeout);

        if (options.getInternalMaxBandwidth() == 0) {
            // unlimited: just omit the parameter
        } else if (options.getInternalMaxBandwidth() > 0) {
            this.addParameter("LS_requested_max_bandwidth", options.getInternalMaxBandwidth());
        }
        
        setSession(session);
        
        addParameter("LS_recovery_from", sessionRecoveryProg);
    }

    @Override
    public String getRequestName() {
        return "bind_session";
    }

    @Override
    public boolean isSessionRequest() {
        return true;
    }
}
