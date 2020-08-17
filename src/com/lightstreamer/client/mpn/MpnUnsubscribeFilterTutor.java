package com.lightstreamer.client.mpn;

import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.mpn.MpnManager.MpnRequestManager;
import com.lightstreamer.client.mpn.MpnManager.MpnUnsubscribeFilter;
import com.lightstreamer.client.session.SessionThread;

/**
 * Tutor managing the deletion of the subscriptions satisfying the filter 
 * (see {@link LightstreamerClient#unsubscribeMpnSubscriptions(String)}).
 */
public class MpnUnsubscribeFilterTutor extends MpnTutor {

    private final MpnUnsubscribeFilter filter;

    public MpnUnsubscribeFilterTutor(long timeoutMs, SessionThread thread, MpnRequestManager mpnRequestManager, MpnUnsubscribeFilter filter, TutorContext context) {
        super(timeoutMs, thread, mpnRequestManager, context);
        this.filter = filter;
    }

    @Override
    protected void doRecovery() {
        mpnRequestManager.sendUnsubscribeFilteredRequest(timeoutMs, filter);
    }
    
    @Override
    public void onResponse() {
        super.onResponse();
        mpnRequestManager.onUnsubscriptionFilterResponse(filter);
    }
}
