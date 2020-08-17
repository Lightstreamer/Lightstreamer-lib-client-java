package com.lightstreamer.client.protocol;

import com.lightstreamer.client.Constants;
import com.lightstreamer.client.requests.ReverseHeartbeatRequest;
import com.lightstreamer.client.requests.VoidTutor;
import com.lightstreamer.client.session.InternalConnectionOptions;
import com.lightstreamer.client.session.SessionThread;
import com.lightstreamer.log.LogManager;
import com.lightstreamer.log.Logger;
import com.lightstreamer.util.Assertions;

/**
 * A timer sending reverse heartbeats.
 * <p>
 * A heartbeat is sent only if the time elapsed from the last sending of a control request is bigger than heartbeat interval.
 * <p>
 * The maximum interval between heartbeats is determined by the parameter LS_inactivity_millis set by the bind_session request
 * and doesn't change during the life of the corresponding session.
 * However the interval can be diminished by the user.
 * <p>
 * <i>To build well-formed heartbeat requests, heartbeats are sent only after the bind session request has been issued 
 * so we are sure that {@literal sessionId} and {@literal serverInstanceAddress} properties are set.</i>
 * 
 * 
 * @since October 2017
 */
public class ReverseHeartbeatTimer {
    
    private static final Logger log = LogManager.getLogger(Constants.HEARTBEAT_LOG);

    private final SessionThread sessionThread;
    private final InternalConnectionOptions options;
    /**
     * Maximum interval. Value of LS_inactivity_millis.
     */
    private final long maxIntervalMs;
    /**
     * It is the minimum between LS_inactivity_millis and the interval chosen by the user.
     */
    private long currentIntervalMs = -1;
    private boolean disableHeartbeats = false;
    private boolean closed = false;
    /**
     * Last time a request has been sent to the server.
     */    
    private long lastSentTimeNs = -1;
    /**
     * The timer assures that there is at most one scheduled task by keeping a phase counter
     * (there is no scheduled task when heartbeats are disabled).
     * When the user changes the interval (see method onChangeInterval), the counter is incremented
     * so that if there is a scheduled task, it is discarded since the task phase is less than the phase counter
     * (see class ScheduledTask).
     */
    private int currentPhase = 0;
    /**
     * True when the bind session request is sent.
     * <br>
     * NB Heartbeats can be sent only when this flag is set.
     */
    private boolean bindSent = false;

    public ReverseHeartbeatTimer(SessionThread sessionThread, InternalConnectionOptions options) {
        assert Assertions.isSessionThread();
        this.sessionThread = sessionThread;
        this.options = options;
        this.maxIntervalMs = options.getReverseHeartbeatInterval();
        if (log.isDebugEnabled()) {
            log.debug("rhb max interval " + maxIntervalMs);
        }
        setCurrentInterval(maxIntervalMs);
    }
    
    /**
     * Must be called just before the sending of a bind session request.
     * @param bindAsControl when true the time a bind_session request is sent is recorded as it is a control request
     * (see {@link #onControlRequest()})
     */
    public void onBindSession(boolean bindAsControl) {
        assert Assertions.isSessionThread();
        if (bindAsControl) {
            /*
             * since schedule() uses lastSentTimeNs,
             * it is important to set lastSentTimeNs before 
             */
            lastSentTimeNs = System.nanoTime();
        }
        if (! bindSent) {
            bindSent = true;
            schedule();            
        }
    }
    
    /**
     * Must be called when the user modifies the interval.
     */
    public void onChangeInterval() {
        assert Assertions.isSessionThread();
        long newInterval = options.getReverseHeartbeatInterval();
        setCurrentInterval(newInterval);
    }

    /**
     * Must be called when a control request is sent.
     */
    public void onControlRequest() {
        assert Assertions.isSessionThread();
        lastSentTimeNs = System.nanoTime();
        if (log.isDebugEnabled()) {            
            log.debug("rhb updated");
        }
    }
    
    /**
     * Must be called when the session is closed.
     */
    public void onClose() {
        assert Assertions.isSessionThread();
        closed = true;
        if (log.isDebugEnabled()) {            
            log.debug("rhb closed");
        }
    }
    
    public long getMaxIntervalMs() {
        return maxIntervalMs;
    }
    
    private void schedule() {
        if (disableHeartbeats || closed) {
            return;
        }
        if (lastSentTimeNs == -1) {
            /*
             * If lastSentTimeNs was not already set, 
             * assume that this is the point from which measuring heartbeat distance.
             * This can happen when the onBindSession() is called with bindAsControl set to false.
             */
            lastSentTimeNs = System.nanoTime();
            submitTask(currentIntervalMs);
        } else {
            long timeLeftMs = getTimeLeftMs();
            if (timeLeftMs <= 0) {
                sendHeartbeat();
                submitTask(currentIntervalMs);
            } else {
                submitTask(timeLeftMs);
            }
        }
    }
    
    private long getTimeLeftMs() {
        assert lastSentTimeNs != -1;
        assert currentIntervalMs != -1;
        long timeElapsedMs = (System.nanoTime() - lastSentTimeNs) / 1_000_000; // convert to millis
        long timeLeftMs = currentIntervalMs - timeElapsedMs;
        return timeLeftMs;
    }
    
    /**
     * Sends a heartbeat message.
     */
    private void sendHeartbeat() {
        if (log.isDebugEnabled()) {
            log.debug("rhb heartbeat ph " + currentPhase);
        }
        sessionThread.getSessionManager().sendReverseHeartbeat(new ReverseHeartbeatRequest(), new VoidTutor(sessionThread, options));
    }
    
    /**
     * Sets the heartbeat interval and schedules a task sending heartbeats if necessary.
     */
    private void setCurrentInterval(long newIntervalMs) {
        assert maxIntervalMs != -1;
        long oldIntervalMs = currentIntervalMs;
        /*
         * Change the current interval with respect to the user defined value and the maximum interval.
         * 
         * newInterval      currentIntervalMs   maxIntervalMs   new currentIntervalMs
         * --------------------------------------------------------------------------------------------------
         * ∞                ∞                   ∞               ∞
         * ∞                ∞                   m               impossible: currentIntervalMs > maxIntervalMs
         * ∞                c                   ∞               ∞
         * ∞                c                   m               m
         * u                ∞                   ∞               u
         * u                ∞                   m               impossible: currentIntervalMs > maxIntervalMs
         * u                c                   ∞               u
         * u                c                   m               minimum(u, m)
         * 
         * ∞ = interval is 0
         * u, c, m = interval bigger than 0
         */
        if (newIntervalMs == 0) {
            currentIntervalMs = maxIntervalMs;
        } else if (maxIntervalMs == 0) {
            assert newIntervalMs > 0;
            currentIntervalMs = newIntervalMs;
        } else {
            assert newIntervalMs > 0 && maxIntervalMs > 0;
            currentIntervalMs = Math.min(newIntervalMs, maxIntervalMs);
        }
        disableHeartbeats = (currentIntervalMs == 0);
        if (oldIntervalMs != currentIntervalMs) {
            if (log.isDebugEnabled()) {
                log.debug("rhb current interval " + currentIntervalMs);
            }
            if (bindSent) {                
                /* 
                 * since currentIntervalMs has changed,
                 * increment phase to discard already scheduled tasks 
                 */
                currentPhase++;
                schedule();
            }
        }
    }
    
    /**
     * Adds a heartbeat task on session thread after the given delay.
     */
    private void submitTask(long scheduleTimeMs) {
        if (log.isDebugEnabled()) {            
            log.debug("rhb scheduled +" + scheduleTimeMs + " ph " + currentPhase);
        }
        sessionThread.schedule(new ScheduledTask(), scheduleTimeMs);
    }
    
    /**
     * A task representing a scheduled heartbeat.
     * The phase assures that there is at most one active task.
     * <p>
     * NB After sending a heartbeat, the task schedules another task instance to simulate a periodic behavior. 
     */
    private class ScheduledTask implements Runnable {
        
        private final int phase = currentPhase;
        
        @Override
        public void run() {
            assert Assertions.isSessionThread();
            if (log.isDebugEnabled()) {
                log.debug("rhb task fired ph " + phase);
            }
            if (phase < currentPhase) {
                if (log.isDebugEnabled()) {            
                    log.debug("rhb task discarded ph " + phase);
                }
                return;
            }
            assert phase == currentPhase;
            schedule();
        }
    }
}