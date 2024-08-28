package com.ericsson.nms.rv.core.amos.operators.http;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.http.conn.ssl.TrustStrategy;

/**
 * @author ekarpia
 * <p>
 * This class is used to trust all
 * @see TrustStrategy
 */
public class TrustAllStrategy implements TrustStrategy {

    @Override
    public boolean isTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
        return true;
    }
}