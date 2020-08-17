package com.lightstreamer.client.mpn;

/**
 * An MPN request of resetting the badge.
 */
public class MpnResetBadgeRequest extends MpnRequest {
    
    public MpnResetBadgeRequest(String deviceId) {
        addParameter("LS_op", "reset_badge");
        addParameter("PN_deviceId", deviceId);
    }
}
