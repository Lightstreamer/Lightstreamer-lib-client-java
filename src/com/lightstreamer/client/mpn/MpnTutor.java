package com.lightstreamer.client.mpn;

import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.protocol.TextProtocol.MpnRequestListener;
import com.lightstreamer.client.requests.RequestTutor;
import com.lightstreamer.client.session.SessionThread;

/**
 * Superclass of all the MPN tutors.
 * <p>
 * <b>NB</b> An MPN tutor is designed to stop retransmitting a request when the corresponding REQOK/REQERR is received.
 * Thus it is important that the {@link MpnRequestListener} of the request calls the method {@link #onResponse()}
 * at the end of the methods {@link MpnRequestListener#onOK()} and {@link MpnRequestListener#onError(int, String)}.
 */
public abstract class MpnTutor extends RequestTutor {

    protected boolean responseReceived = false;
    protected final MpnRequestManager mpnRequestManager;
    protected final TutorContext tutorContext;
    
    /**
     * An instance of the class is shared by all the tutors created within a session.
     * When the MpnManager closes a session, by setting the {@code dismissed} flag to true
     * it signals that all the tutors in the context must stop to retransmit.
     */
    static class TutorContext {
        boolean dismissed = false;
    }
    
    public MpnTutor(long timeoutMs, SessionThread thread, MpnRequestManager mpnRequestManager, TutorContext context) {
        super(timeoutMs, thread, null);
        this.mpnRequestManager = mpnRequestManager;
        this.tutorContext = context;
    }
    
    /**
     * Should be called when a REQOK/REQERR is received to stop retransmissions.
     */
    public void onResponse() {
        responseReceived = true;
    }
    
    /*
     * Overridden methods.
     */

    @Override
    public boolean shouldBeSent() {
        return ! tutorContext.dismissed;
    }

    @Override
    protected boolean verifySuccess() {
        // a request must be retransmitted only if 1) the server has not responded (with either REQOK or REQERR)
        // and 2) the tutor is not dismissed 
        return responseReceived || tutorContext.dismissed;
    }

    @Override
    public void notifyAbort() {
        // process the abort as a (sort of) response
        responseReceived = true;
    }

    @Override
    protected boolean isTimeoutFixed() {
        return false;
    }

    @Override
    protected long getFixedTimeout() {
        return 0;
    }
}
