package com.lightstreamer.client.mpn;

import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.session.SessionThread;

/**
 * Tutor of MPN reset requests.
 */
public class MpnResetBadgeTutor extends MpnTutor {

    public MpnResetBadgeTutor(long timeoutMs, SessionThread thread, MpnRequestManager mpnRequestManager, TutorContext context) {
        super(timeoutMs, thread, mpnRequestManager, context);
    }

    @Override
    protected void doRecovery() {
        mpnRequestManager.sendResetBadgeRequest(timeoutMs);
    }
}
