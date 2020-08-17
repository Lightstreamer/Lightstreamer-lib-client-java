package com.lightstreamer.client.mpn;

import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.session.SessionThread;

/**
 * Tutor of MPN subscription requests.
 */
public class MpnSubscribeTutor extends MpnTutor {

    private final String ephemeralSubId;
    private final MpnSubscription subscription;

    public MpnSubscribeTutor(long timeoutMs, SessionThread thread, String ephemeralSubId, MpnSubscription sub, MpnRequestManager mpnRequestManager, TutorContext context) {
        super(timeoutMs, thread, mpnRequestManager, context);
        this.ephemeralSubId = ephemeralSubId;
        this.subscription = sub;
    }

    @Override
    protected void doRecovery() {
        mpnRequestManager.sendSubscribeRequest(timeoutMs, ephemeralSubId, subscription);
    }
}
