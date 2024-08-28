package com.ericsson.nms.rv.core.amos.operators.http;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.ssl.SSLContextBuilder;

/**
 * @author eamgmuh This class initialise HTTP client with all appropriate configuration.
 */
public class CustomHttpClient {

    // HTTP default timeout
    private static final int HTTP_CLIENT_TIMEOUT = 20 * 1000; // 20 seconds
    private static CustomHttpClient INSTANCE;

    /**
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    private CustomHttpClient() {

    }

    /**
     * Used in tests steps
     *
     * @return {@code CustomHttpClient}
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    public static CustomHttpClient getConnectionManager() {
        if (INSTANCE == null) {
            INSTANCE = new CustomHttpClient();
        }
        return INSTANCE;
    }

    /**
     * Returns SSLConnectionSocketFactory, which trusts all certificates and hosts
     *
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws KeyManagementException
     */
    private static SSLConnectionSocketFactory getTrustAllSSLConnectionSocketFactory()
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        final SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustAllStrategy());
        builder.useProtocol("TLS");

        return new SSLConnectionSocketFactory(builder.build(), new TrustAllHostnameVerifier());
    }

    /**
     * Returns instance of CloseableHttpClient
     * <p>
     * needs to use setConnectionManagerShared(true), as connection manager is used by multiple clients
     *
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    public CloseableHttpClient getHttpClient(final boolean followRedirect)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        final BasicCookieStore cookieStore = new BasicCookieStore();

        RequestConfig config = RequestConfig.custom().setConnectTimeout(HTTP_CLIENT_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_CLIENT_TIMEOUT).setSocketTimeout(HTTP_CLIENT_TIMEOUT).build();

        return HttpClientBuilder.create().setDefaultRequestConfig(config).setDefaultCookieStore(cookieStore)
                .setRedirectStrategy(followRedirect ? new LaxRedirectStrategy() : DefaultRedirectStrategy.INSTANCE)
                .setSSLSocketFactory(getTrustAllSSLConnectionSocketFactory()).evictExpiredConnections().build();

    }
}
