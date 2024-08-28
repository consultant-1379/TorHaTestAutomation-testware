package com.ericsson.nms.rv.core.amos.operators.http;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * @author eamgmuh
 * <p>
 * This class verifies all hostnames
 * @see HostnameVerifier
 */
public class TrustAllHostnameVerifier implements HostnameVerifier {

    /**
     * @see HostnameVerifier#verify(String, SSLSession)
     */
    @Override
    public boolean verify(final String hostname, final SSLSession session) {
        return true;
    }

}