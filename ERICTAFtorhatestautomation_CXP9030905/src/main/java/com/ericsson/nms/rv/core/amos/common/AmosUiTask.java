/**
 *
 */
package com.ericsson.nms.rv.core.amos.common;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.UpgradeException;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.EnmSystemException;
import com.ericsson.nms.rv.core.amos.operators.enm.LoginLogoutOperator;
import com.ericsson.nms.rv.core.amos.operators.http.AMOSUILoadTestSocket;
import com.google.common.base.Preconditions;

/**
 * @author ekarpia
 * modified according to official guide
 * https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html
 */
public class AmosUiTask {

    public static final long WEBSOCKET_CLIENT_CONNECT_TIMEOUT = 60 * 1000L; // 60 seconds
    public static final long WEBSOCKET_CLIENT_IDLE_TIMEOUT = 4 * 60 * 1000L; // 4 min!
    public static final int STANDARD_DELAY_BETWEEN_HTTP_EXECUTIONS = 3000;
    private static final Logger logger = LogManager.getLogger(AmosUiTask.class);
    private static final int WEBSOCKETS_TIMEOUT = 120;
    private final CloseableHttpClient httpClient;
    private final EnmApplication application;
    private final String username;
    private final String password;
    private final String enmHost;
    private final String nodeAddress;
    private int total = 0;

    /**
     * @param username
     * @param password
     * @param enmHost
     * @param nodeAddress
     */
    public AmosUiTask(final String username, final String password, final CloseableHttpClient httpClient, final String enmHost, final String nodeAddress, final EnmApplication application) {
        this.username = username;
        this.password = password;
        this.httpClient = httpClient;
        this.nodeAddress = nodeAddress;
        this.enmHost = enmHost;
        this.application = application;
    }

    public Boolean call() throws EnmSystemException {

        logger.info("User {}, node {}", username, nodeAddress);

        WebSocketClient webSocketsClient = null;
        Boolean callSucceeded = false;
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {

            final Map<String, String> requestHeaders = new HashMap<>();
            requestHeaders.put("Host", enmHost);

            final LoginLogoutOperator loginOperator = new LoginLogoutOperator();
            boolean loginSuccess;
            try {
                loginSuccess = loginOperator.login(httpClient, username, password, enmHost);
            } catch (final Exception e) {
                throw new EnmSystemException("Error trying to login in ENM", e);
            }

            if (!loginSuccess) {
                throw new EnmSystemException("Error trying to login in ENM: loging was not successful ");
            }

            // access shell terminal
            makeGetRequest("/#advancedmoscripting?shell", requestHeaders);
            // Starting AMOS request sent successfully
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);

            // get credentials
            makeGetRequest("/amos-service/amos/credentials", requestHeaders);
            // Getting credentials request sent successfully
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);

            // use websocket client

            // enable SSL (wss) support for websockets, but trust all
            // certificates

            final SslContextFactory ssl = new SslContextFactory(true);

            webSocketsClient = new WebSocketClient(ssl, executorService);

            // set connect timeout
            webSocketsClient.setConnectTimeout(WEBSOCKET_CLIENT_CONNECT_TIMEOUT);
            // set read timeout
            webSocketsClient.setMaxIdleTimeout(WEBSOCKET_CLIENT_IDLE_TIMEOUT);

            // copy all cookies
            final CookieStore cookieStore = new HttpCookieStore();

            // create socket
            final AMOSUILoadTestSocket socket = new AMOSUILoadTestSocket(nodeAddress, username, application);
            // times depends on workload cycles

            establishWebSocket(cookieStore, webSocketsClient, socket);
            // Web socket established successfully
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);

            logger.info("COOKIES HERE");
            if (cookieStore.getCookies().isEmpty()) {
                logger.info("No cookies!");
            } else {
                for (final HttpCookie cookie : cookieStore.getCookies()) {
                    logger.info(" - {}", cookie.getValue());
                }
            }

            socket.awaitClose(WEBSOCKETS_TIMEOUT, SECONDS); // Wait

            if (socket.isAmosLaunched()) {
                logger.info("Test passed for user : {} ", username);
                logger.info("Total users passed: {}", ++total);
                callSucceeded = true;
            } else {
                logger.info("Test NOT passed for user : {} ", username);
                callSucceeded = false;
            }

        } catch (final UpgradeException e) {
            logger.error("For user {},\ncouldn't establish websockets connection, reason: {}", username, e.getMessage(), e);
            callSucceeded = false;
        } catch (final EnmSystemException ex) {
            logger.error("For user {},\nerror {} occured", username, ex.getMessage(), ex);
            callSucceeded = false;
            throw new EnmSystemException("login logout has failed for AMOS");
        } catch (final Exception ex) {
            logger.error("For user {},\nerror {} occured", username, ex.getMessage(), ex);
            callSucceeded = false;
        } finally {
            try {
                if (webSocketsClient != null) {
                    webSocketsClient.stop();
                }
                executorService.shutdown();
                boolean successful = executorService.awaitTermination(30, SECONDS);
                Preconditions.checkState(successful, "Unable to shutdown Executor Service");
            } catch (final Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        return callSucceeded;
    }

    /**
     * Send GET request
     *
     * @param path
     * @param requestHeaders
     * @throws IOException
     * @throws URISyntaxException
     */
    private void makeGetRequest(final String path, final Map<String, String> requestHeaders) throws IOException, URISyntaxException {

        final RequestBuilder reqBuilder = RequestBuilder.get(path).setUri(new URI("https://" + this.enmHost));

        if (!requestHeaders.isEmpty()) {
            for (final Map.Entry<String, String> oneEntry : requestHeaders.entrySet()) {
                final String key = oneEntry.getKey();
                final String value = oneEntry.getValue();
                reqBuilder.addHeader(key, value);
            }
        }

        final HttpUriRequest request = reqBuilder.build();

        final CloseableHttpResponse response = httpClient.execute(request);

        final HttpEntity responseEntity = response.getEntity();

        String responseBody = null;

        if (responseEntity != null) {
            responseBody = EntityUtils.toString(responseEntity);
        }

        logger.info("\n\nResponse code: {}\nStatus line: {}\n", response.getStatusLine().getStatusCode(), response.getStatusLine());

    }

    /**
     * Create new key for WebSockets handshake.
     * Used from Jetty ClientUpgradeRequest
     *
     * @return WebSockets handshake key (Base64 encoded).
     */
    private String genRandomKey() {
        final byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return new String(B64Code.encode(bytes));
    }

    /**
     * this method establishes the web socket with ENM server
     *
     * @throws URISyntaxException
     * @throws InterruptedException
     * @throws IOException
     */
    private void establishWebSocket(final CookieStore cookieStore, final WebSocketClient webSocketsClient, final AMOSUILoadTestSocket socket) throws URISyntaxException, InterruptedException, IOException {
        try {
            // generate websockets key
            final String secWebSocketKey = this.genRandomKey();

            // create required URIs
            final String webSocketsURL = "wss://" + this.enmHost + "/terminal-websocket/command/" + username;
            final URI webSocketsURI = new URI(webSocketsURL);

            final String cookieDomain = this.enmHost;

            final URI cookieUri = new URI(cookieDomain);

            final HttpClientContext httpClientContext = HttpClientContext.create();

            final RequestBuilder reqBuilder = RequestBuilder.get("/").setUri(new URI("https://" + this.enmHost));

            httpClient.execute(reqBuilder.build(), httpClientContext);

            // Web socket request sent successfully
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);

            final List<Cookie> cookies = httpClientContext.getCookieStore().getCookies();

            for (final Cookie cookie : cookies) {
                final HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());

                logger.info("cookie name: {} | value: {}", httpCookie.getName(), httpCookie.getValue());

                // as Jetty Websocket client filters out cookies, that are
                // not in request URI
                // ClientUpgradeRequest.setCookiesFrom()
                // cookies that belong to -> e.g. .ericsson.se, will be
                // removed from request
                // so we need to manually update cookies references,
                // as this websocket client does not support global domain
                // cookies .ericsson.se

                httpCookie.setVersion(0); // This is necessary to avoid double quotes in the request!!!
                httpCookie.setDomain(cookieDomain);
                httpCookie.setPath(cookie.getPath());
                cookieStore.add(cookieUri, httpCookie);
            }

            // add tor cookie
            final HttpCookie torCookie = new HttpCookie("TorUserID", username);
            torCookie.setDomain(this.enmHost);
            torCookie.setPath("/");

            cookieStore.add(cookieUri, torCookie);

            logger.info("cookieUri : {} torCookie : {}", cookieUri, torCookie);
            logger.info("Retrieved all cookies information. Closing http client.");

            webSocketsClient.setCookieStore(cookieStore);

            // create handshake
            final ClientUpgradeRequest request = new ClientUpgradeRequest();

            request.setHeader("Sec-WebSocket-Key", secWebSocketKey);
            request.setHeader("Sec-WebSocket-Protocol", "any-protocol");
            request.setHeader("Sec-WebSocket-Extensions", Arrays.asList("permessage-deflate", "client_max_window_bits"));

            try {
                // close http client, as we do not need http connection anymore
                httpClient.close();
                webSocketsClient.start();
            } catch (final Exception e) {
                throw new ClientProtocolException(e);
            }

            // connect to websockets
            webSocketsClient.connect(socket, webSocketsURI, request);
            // Web socket connected successfully
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);

            logger.info("Connecting to : {} for user {}", webSocketsURI, username);

        } catch (final IOException e) {
            logger.error("Exception thrown while trying to connect websocket", e);
            throw e;
        }
    }

}
