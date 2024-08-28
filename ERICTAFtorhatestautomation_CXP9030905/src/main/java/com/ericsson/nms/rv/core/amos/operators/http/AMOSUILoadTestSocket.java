/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2016
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.nms.rv.core.amos.operators.http;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;

/**
 * This socket works with ENM shell to overload the server with Amos shell work: after launching amos on a node from the shell and doing a few preparative steps, the socket enters a long loop that reiterates a sequence of commands on AMOS shell for
 * {@value #CYCLES_AMOUNT} times.<br/>
 * The loop must be forced to exit from outside, by killing the container thread.
 *
 * @author eluctam
 */
@WebSocket(maxTextMessageSize = 512 * 1024)
public final class AMOSUILoadTestSocket {

    private static final String EOL = "\n";

    /**
     * Command to launch amos from ENM shell. <br/>
     * <b>%s</b> should be the ip address of the node against which we are launching amos.
     */
    private static final String LAUNCH_AMOS_ON_SHELL = "amos %s" + EOL;

    /**
     * One entire workload sequence & for AMOS test we need at least two commands that we are able to validate first commmand's response
     */
    private static final Logger logger = LogManager.getLogger(AMOSUILoadTestSocket.class);

    private final CountDownLatch closeLatch;
    private final String nodeAddress;
    private final String userName;
    private final EnmApplication application;
    private Session session;
    private String sentMessage;
    private boolean isAmosLaunched;

    public AMOSUILoadTestSocket(final String nodeAddress, final String userName, final EnmApplication application) {
        this.closeLatch = new CountDownLatch(1);
        this.nodeAddress = nodeAddress;
        this.sentMessage = null;
        this.userName = userName;
        this.isAmosLaunched = false;
        this.application = application;
    }

    public boolean awaitClose(final int duration, final TimeUnit unit) throws InterruptedException {
        return this.closeLatch.await(duration, unit);
    }

    @OnWebSocketClose
    public void onClose(final int statusCode, final String reason) {
        logger.info("onClose -> Connection closed: {} - {}", statusCode, reason);
        // Double checking the session status
        if (session != null && session.isOpen()) {
            session.close();
        }
        this.session = null;
        this.closeLatch.countDown();
    }

    @OnWebSocketConnect
    public void onConnect(final Session session) {
        this.session = session;
        try {
            // Successfully connected to the server
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);

            isAmosLaunched = false;
            // Now we should be on ENM shell, we want to launch amos
            logger.info("Websocket connection is successful, trying to launch the amos application");
            session.getRemote().sendString(String.format(LAUNCH_AMOS_ON_SHELL, nodeAddress));
            logger.info("Application launch successful");

            // AMOS shell should now be launched...

        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            endSession();
        }
    }

    @OnWebSocketMessage
    public void onMessage(final String msg) {
        try {
            if (this.closeLatch.getCount() <= 0) {
                return;
            }
            // Received message from server
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);

            if (this.sentMessage != null && msg.trim().equals(this.sentMessage.trim())) {
                logger.info(msg);
            } else {
                onReceiveMessage(msg);
            }

        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            endSession();
        }

    }

    protected void onReceiveMessage(final String msg) throws IOException {
        logger.info(msg);

        if (msg.contains("Checking ip contact...Not OK") || msg.contains("Not OK")) {
            isAmosLaunched = true;
            logger.error("IP address: {} not reachable from AMOS! Socket connection will be terminated...", this.nodeAddress);
            endSession();
        } else if (msg.contains("Checking ip contact...OK") || msg.contains("OK")) {
            isAmosLaunched = true;
            logger.info("IP address: {} reachable from AMOS! AMOS launched successfully", this.nodeAddress);
            endSession();
        }

    }

    @OnWebSocketError
    public void onError(final Throwable error) {
        logger.error("For user {}, error {} occured", this.userName, error.getMessage(), error);
        isAmosLaunched = false;
        endSession();
    }

    /**
     * end opened session and return control to calling function
     */
    private void endSession() {
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
            logger.info("Socket for {} closed successfully", nodeAddress);
        } finally {
            this.closeLatch.countDown();
        }
    }

    /**
     * Checks if AMOS launched successfully or not
     *
     * @return boolean value
     */
    public boolean isAmosLaunched() {
        return isAmosLaunched;
    }

}
