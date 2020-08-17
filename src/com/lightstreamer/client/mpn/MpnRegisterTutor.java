package com.lightstreamer.client.mpn;

import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.session.SessionThread;

/**
 * Tutor of MPN registration requests.
 */
public class MpnRegisterTutor extends MpnTutor {
    
    public MpnRegisterTutor(long timeoutMs, SessionThread thread, MpnRequestManager mpnRequestManager, TutorContext context) {
        super(timeoutMs, thread, mpnRequestManager, context);
    }

    @Override
    protected void doRecovery() {
        mpnRequestManager.sendRegisterRequest(timeoutMs);
    }
}
