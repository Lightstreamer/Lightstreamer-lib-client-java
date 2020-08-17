package com.lightstreamer.client.session;

import com.lightstreamer.client.requests.RecoverSessionRequest;

/**
 * Bean about the status of the recovery attempt.
 * <p>
 * State graph of the bean. The event start=T means the client wants to recover the current session.
 * Transitions not depicted should not happen.
<pre>
       start=F                            start=T
       +--+                               +--+
       |  |                               |  |
       |  |                               |  |
    +--+--v------+   start=T/set ts    +--+--v-----+
    |recovery=F  +--------------------->recovery=T |
    |            +<--------------------+           |
    +------------+   start=F/reset ts  +-----------+
</pre>
 *
 * 
 * @since May 2018
 */
class RecoveryBean {

    /**
     * The flag is true when the session has been created to recover the previous session,
     * which was discarded because of a network error.
     * The first request sent by this session is a {@link RecoverSessionRequest}.
     */
    private boolean recovery;
    /**
     * Start time of the recovery process.
     */
    private long recoveryTimestampNs;
    
    private boolean invariant() {
        return recovery ? recoveryTimestampNs != -1 : recoveryTimestampNs == -1;
    }
    
    /**
     * Initial state. No recovery.
     */
    RecoveryBean() {
        this.recovery = false;
        this.recoveryTimestampNs = -1;
        assert invariant();
    }
    
    /**
     * Next state.
     */
    RecoveryBean(boolean startRecovery, RecoveryBean old) {
        if (old.recovery) {
            if (startRecovery) {
                recovery = true;
                recoveryTimestampNs = old.recoveryTimestampNs;
            } else {
                /*
                 * This case can occur when, for example, after a recovery
                 * the client rebinds in HTTP because the opening of Websockets takes too long. 
                 */
                recovery = false;
                recoveryTimestampNs = -1;
            }
            
        } else {
            if (startRecovery) {
                this.recovery = true;
                this.recoveryTimestampNs = System.nanoTime();
                
            } else {
                assert old.recoveryTimestampNs == -1;
                this.recovery = false;
                this.recoveryTimestampNs = -1;
            }
        }
        assert invariant();
    }
    
    /**
     * Restore the time left to complete a recovery, i.e. calling timeLeftMs(maxTimeMs) returns maxTimeMs.
     * The method must be called when a recovery is successful.
     */
    void restoreTimeLeft() {
        recovery = false;
        recoveryTimestampNs = -1;
    }
    
    /**
     * True when the session has been created to recover the previous session,
     * which was discarded because of a network error.
     */
    boolean isRecovery() {
        return recovery;
    }
    
    /**
     * Time left to recover the session.
     * When zero or a negative value, the session must be discarded.
     */
    long timeLeftMs(long maxTimeMs) {
        if (recovery) {
            assert recoveryTimestampNs != -1;
            return (maxTimeMs * 1_000_000 - (System.nanoTime() - recoveryTimestampNs)) / 1_000_000;
            
        } else {
            return maxTimeMs;
        }
    }
}
