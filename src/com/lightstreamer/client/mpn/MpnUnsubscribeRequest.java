package com.lightstreamer.client.mpn;

/**
 * An MPN request to delete a subscription.
 */
public class MpnUnsubscribeRequest extends MpnRequest {
    
    private final String subscriptionId;
    
    public MpnUnsubscribeRequest(String deviceId, MpnSubscription sub) {
        subscriptionId = sub.getSubscriptionId();
        addParameter("LS_op", "deactivate");
        addParameter("PN_deviceId", deviceId);
        addParameter("PN_subscriptionId", sub.getSubscriptionId());
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }
}
