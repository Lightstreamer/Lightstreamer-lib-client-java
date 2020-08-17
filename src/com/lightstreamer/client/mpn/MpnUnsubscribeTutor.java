package com.lightstreamer.client.mpn;

import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.session.SessionThread;

/**
 * Tutor of MPN unsubscription requests.
 */
public class MpnUnsubscribeTutor extends MpnTutor {

    private final MpnSubscription subscription;

    public MpnUnsubscribeTutor(long timeoutMs, SessionThread thread, MpnSubscription sub, MpnRequestManager mpnRequestManager, TutorContext context) {
        super(timeoutMs, thread, mpnRequestManager, context);
        this.subscription = sub;
    }

    @Override
    protected void doRecovery() {
        mpnRequestManager.sendUnsubscribeRequest(timeoutMs, subscription);
    }
}
