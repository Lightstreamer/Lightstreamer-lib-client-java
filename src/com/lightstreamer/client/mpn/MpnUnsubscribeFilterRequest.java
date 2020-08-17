package com.lightstreamer.client.mpn;

/**
 * An MPN request to delete the subscriptions satisfying a filter (the possible values are ALL, ACTIVE or TRIGGERED).
 */
public class MpnUnsubscribeFilterRequest extends MpnRequest {
    
    public MpnUnsubscribeFilterRequest(String deviceId, String filter) {
        assert filter.equals("ALL") || filter.equals("ACTIVE") || filter.equals("TRIGGERED");
        addParameter("LS_op", "deactivate");
        addParameter("PN_deviceId", deviceId);
        if (! "ALL".equals(filter)) {            
            addParameter("PN_subscriptionStatus", filter);
        }
    }
}
