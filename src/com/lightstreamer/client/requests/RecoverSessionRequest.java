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
