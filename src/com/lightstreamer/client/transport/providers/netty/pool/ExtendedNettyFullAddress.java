package com.lightstreamer.client.transport.providers.netty.pool;

import java.util.Map;

import com.lightstreamer.client.ConnectionOptions;
import com.lightstreamer.client.LightstreamerClient;
import com.lightstreamer.client.transport.providers.netty.NettyFullAddress;

/**
 * The address of a remote Lightstreamer server storing also the configurations about proxy, extra headers and cookies
 * (see {@link ConnectionOptions#setProxy(com.lightstreamer.client.Proxy)}, {@link ConnectionOptions#setHttpExtraHeaders(Map)}
 * and {@link LightstreamerClient#addCookies(java.net.URI, java.util.List)}).
 * <p>
 * <b>NB</b> To be equal, two objects of this type must have addresses, proxies, extra headers and cookies equal.
 * 
 * 
 * @since August 2017
 */
public class ExtendedNettyFullAddress {

    /*
     * NB
     * Since these objects are used as keys in maps,
     * it is important to regenerate the methods hashCode and equals 
     * if the attributes change.
     */
    
    private final NettyFullAddress address;
    private final Map<String, String> extraHeaders;
    private final String cookies;

    public ExtendedNettyFullAddress(NettyFullAddress address, Map<String, String> extraHeaders, String cookies) {
        this.address = address;
        this.extraHeaders = extraHeaders;
        this.cookies = cookies;
    }
    
    public NettyFullAddress getAddress() {
        return address;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }
    
    public String getCookies() {
        return cookies;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((address == null) ? 0 : address.hashCode());
        result = prime * result + ((cookies == null) ? 0 : cookies.hashCode());
        result = prime * result + ((extraHeaders == null) ? 0 : extraHeaders.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ExtendedNettyFullAddress other = (ExtendedNettyFullAddress) obj;
        if (address == null) {
            if (other.address != null)
                return false;
        } else if (!address.equals(other.address))
            return false;
        if (cookies == null) {
            if (other.cookies != null)
                return false;
        } else if (!cookies.equals(other.cookies))
            return false;
        if (extraHeaders == null) {
            if (other.extraHeaders != null)
                return false;
        } else if (!extraHeaders.equals(other.extraHeaders))
            return false;
        return true;
    }
}
