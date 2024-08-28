package com.ericsson.nms.rv.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.ericsson.nms.rv.core.util.AdminUserUtils;
import com.ericsson.nms.rv.core.util.CommonUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.nms.rv.core.downtime.DownTimeHandler;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.system.SystemObserver;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.services.scriptengine.spi.dtos.AbstractDto;
import com.ericsson.oss.services.scriptengine.spi.dtos.summary.SummaryDto;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.google.common.collect.Lists;

/**
 * {@code EnmApplication} implements common methods used by the ENM web applications.
 */
public abstract class EnmApplication implements SystemObserver {

    public static final int TIMEOUT_REST = HAPropertiesReader.appRestTimeOut;
    public static final int TIMEOUT_REST_100 = 100;
    private static final Logger logger = LogManager.getLogger(EnmApplication.class);
    /**
     * the authentication code for a successful authentication
     */
    private static final String AUTH_CODE_SUCCESS = "0";
    public static final String CLI_COMMAND_FAILED = "Failed to execute the command \"%s\" ";
    /**
     * the URL for executing a CLI command
     */
    public static final String CLI_COMMAND_URL = "/script-engine/services/command";
    /**
     * the URL for retrieving the output of the last CLI command
     */
    public static final String CLI_COMMAND_OUTPUT_URL = CLI_COMMAND_URL + "/output/%s";
    private static final String LOGIN_FAILED = "Login failed: %s";
    private static final String LOGOUT_FAILED_WITH_CODE = "Logout failed: %s";
    public static final List<HttpStatus> acceptableResponse = new ArrayList<>();
    private static final Map<Class, DownTimeHandler> downTimeAppHandler = new ConcurrentHashMap<>();
    private static final DownTimeHandler downTimeSysHandler = new DownTimeHandler();
    private static final Map<Class, AtomicBoolean> firstTimeFail = new ConcurrentHashMap<>();
    private static final int TIMEOUT_LOGIN_LOGOUT = HAPropertiesReader.enmLoginLogoutRestTimeOut;
    private static final String GET = "GET: ";
    /**
     * the signals used for Inter Process Communication
     */
    private static volatile AtomicBoolean SIGNALS = new AtomicBoolean(false);
    private final HttpRestServiceClient httpRestServiceClient = new HttpRestServiceClient();
    private final Class<? extends EnmApplication> clazz;
    private SystemStatus systemStatus;
    private boolean isLoginNeed;
    public  boolean islogoutNeeded = false;

    protected EnmApplication() {
        clazz = getClass();
        acceptableResponse.add(HttpStatus.findByCode(200));
        acceptableResponse.add(HttpStatus.findByCode(201));
        acceptableResponse.add(HttpStatus.findByCode(202));
        acceptableResponse.add(HttpStatus.findByCode(204));
    }

    public static DownTimeHandler getDownTimeSysHandler() {
        return downTimeSysHandler;
    }

    public static Map<Class, DownTimeHandler> getDownTimeAppHandler() {
        return downTimeAppHandler;
    }

    public static Map <String, Map<Date, Date>> getAppDateTimeList = downTimeSysHandler.appDateTimeMapList;
    public static Map <String, Map<Date, Date>> getAppDateTimeTimeoutList = downTimeSysHandler.appDateTimeTimeoutMapList;

    public static Map <String, List<String>> getAppDateTimeLoggerList = downTimeSysHandler.appDateTimeLoggerMapList;
    public static Map <String, List<String>> getAppDateTimeTimeoutLoggerList = downTimeSysHandler.appDateTimeTimeoutLoggerMapList;

    /**
     * Returns {@code true} if the given signal is set.
     *
     * @return {@code true} if the given signal is set
     */
    public static boolean getSignal() {
        return SIGNALS.get();
    }

    /**
     * Sets the given signal.
     * Stops verifiers.
     */
    public static void setSignal() {
        SIGNALS.set(true);
    }

    /**
     * Clears the given signal.
     * Starts verifiers.
     */
    public static void clearSignal() {
        SIGNALS.set(false);
    }

    public static void startSystemDownTime() {
        if(!getDownTimeSysHandler().getDowntimeStarted()) {
            downTimeSysHandler.start(HAPropertiesReader.SYSTEMVERIFIER);
        }
        downTimeAppHandler.forEach((aClass, downTimeHandler) -> {
            if (downTimeHandler != null) {
                downTimeHandler.stop(aClass.getSimpleName(), 0);
            }
        });
        downTimeAppHandler.forEach((aClass, downTimeHandler) -> {
            if (downTimeHandler != null) {
                downTimeHandler.stopTimeout(aClass.getSimpleName(), 0);
            }
        });
        firstTimeFail.forEach((aClass, atomicBoolean) -> {
            if (atomicBoolean != null) {
                atomicBoolean.set(true);
            }
        });
    }

    public static String getUsedNodes(final List<Node> nodeList) {
        return nodeList.stream().map(Node::getNetworkElementId).collect(Collectors.joining(";"));
    }

    public void stopSystemDownTime(final long... responseTimeMillis) {
        //final float adjust = responseTimeMillis != null && responseTimeMillis.length > 0 ? responseTimeMillis[0] / HAconstants.TEN_EXP_3 : 0;
        final float adjust = 0;
        downTimeSysHandler.stop(HAPropertiesReader.SYSTEMVERIFIER, adjust);
    }

    public void stopAppDownTime(final long... responseTimeMillis) {
        final DownTimeHandler downTimeHandler = downTimeAppHandler.get(clazz);
        if (downTimeHandler != null) {
            //final float adjust = responseTimeMillis != null && responseTimeMillis.length > 0 ? responseTimeMillis[0] / HAconstants.TEN_EXP_3 : 0;
            final float adjust = 0;
            downTimeHandler.stop(clazz.getSimpleName(), adjust);
        }
    }

    public void stopAppDownTime(final long time, final Date date) {
        final DownTimeHandler downTimeHandler = downTimeAppHandler.get(clazz);
        if (downTimeHandler != null) {
            downTimeHandler.stopWithTime(clazz.getSimpleName(), time, date);
        }
    }

    public void stopAppTimeoutDownTime(final long... responseTimeMillis) {
        final DownTimeHandler downTimeHandler = downTimeAppHandler.get(clazz);
        if (downTimeHandler != null) {
            //final float adjust = responseTimeMillis != null && responseTimeMillis.length > 0 ? responseTimeMillis[0] / HAconstants.TEN_EXP_3 : 0;
            final float adjust = 0;
            downTimeHandler.stopTimeout(clazz.getSimpleName(), adjust);
        }
    }

    public void startAppDownTime() {
        if (systemStatus.isSSOAvailable() && isLoginSuccess()) {
            downTimeAppHandler.get(clazz).start(clazz.getSimpleName());
        }
    }

    public void startAppTimeoutDowntime() {
        if (systemStatus.isSSOAvailable() && isLoginSuccess()) {
            downTimeAppHandler.get(clazz).startTimeout(clazz.getSimpleName());
        }
    }

    public HttpRestServiceClient getHttpRestServiceClient() {
        return httpRestServiceClient;
    }

    protected final void initDownTimerHandler(final SystemStatus systemStatus) {
        downTimeAppHandler.putIfAbsent(clazz, new DownTimeHandler());
        this.systemStatus = systemStatus;
    }

    protected final void initFirstTimeFail() {
        firstTimeFail.putIfAbsent(clazz, new AtomicBoolean(true));
    }

    /**
     * Executes a CLI command.
     *
     * @param command the command to be executed
     * @return the command output
     * @throws EnmException     if failed to execute the CLI command
     * @throws TimeoutException if the command could not be executed within HAPropertiesReader#getMaxDisruptionTime()}
     */
    public String executeCliCommand(final String command) throws EnmException, HaTimeoutException {

        String body = null;
        try {
            final List<String[]> bodies = new ArrayList<>();
            bodies.add(new String[]{"command", command});

            final String[] header = new String[]{"Skip-Cache", "true", "streaming", "true", "confirmation", "true", "tabId", "do_not_use"};
            final HttpResponse response = httpRestServiceClient.sendPostRequest(TIMEOUT_REST, ContentType.MULTIPART_FORM_DATA, header, bodies, null, CLI_COMMAND_URL);

            if (response != null) {
                analyzeResponseWithoutStopAppDownTime(String.format(CLI_COMMAND_FAILED, command), response);
                final Map<String, String> headers = response.getHeaders();

                final String requestId = headers.get("request_id");
                logger.debug("For command [{}] requestId is : '{}'", command, requestId);

                final List<AbstractDto> commandResponse = getCommandResponse(requestId);

               body = String.valueOf(commandResponse);
            } else {
                logger.warn("For ActionType (POST) and action {} HttpResponse is NULL!", CLI_COMMAND_URL);
            }

        } catch (final IllegalStateException e) {
            logger.warn(String.format(CLI_COMMAND_FAILED, command), e);
            throw new HaTimeoutException(String.format(CLI_COMMAND_FAILED, command), EnmErrorType.APPLICATION);
        } catch (final RuntimeException e) {
            //Allow code to break from an un-recoverable failure, log error first
            logger.error(String.format(CLI_COMMAND_FAILED, command), e);
            throw e;
        }
        return body;
    }

    private List<AbstractDto> getCommandResponse(final String requestId) throws EnmException {
        final List<AbstractDto> result = Lists.newArrayList();
        while (!containsSummary(result)) {
            result.addAll(dtosFromJson(getStream(requestId)));
        }
        return result;
    }

    private String getStream(final String requestId) throws EnmException {
        final String[] queryParam = new String[]{"max_size", "20000"};
        final String[] header = new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON, "wait_interval", "200", "attempts", "150"};
        final HttpResponse response = httpRestServiceClient.sendGetRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, header, null, queryParam, String.format(CLI_COMMAND_OUTPUT_URL, requestId));
        analyzeResponseWithoutStopAppDownTime(String.format(CLI_COMMAND_FAILED, String.format(CLI_COMMAND_OUTPUT_URL, requestId)), response);
        stopAppTimeoutDownTime();
        return response.getBody();
    }

    private Collection<AbstractDto> dtosFromJson(final String json) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            final List<AbstractDto> result = new ArrayList<>();
            for (final JsonNode node : mapper.readTree(json)) {
                result.add(mapper.readValue(node, AbstractDto.class));
            }

            return result;
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private boolean containsSummary(final List<AbstractDto> dtos) {
        return !dtos.isEmpty() && dtos.get(dtos.size() - 1) instanceof SummaryDto;
    }

    public boolean sleepAndCatchInterruptedException() {
        try {
            Thread.sleep(2L * Constants.TEN_EXP_3);
        } catch (final InterruptedException ignore) {
            return true;
        }
        return false;
    }

    private void verifyResponse(final HttpResponse httpResponse, final String success, final String failed) throws EnmException {
        final HttpStatus responseCode = httpResponse.getResponseCode();
        final Map<String, String> headers = httpResponse.getHeaders();
        if (HttpStatus.OK.equals(responseCode) || "0".equals(headers.get("X-AuthErrorCode"))) {
            stopSystemDownTime(httpResponse.getResponseTimeMillis());
            logger.info(success);
        } else {
            logger.error("ResponseCode: {} , Headers: {}", responseCode, headers);
            throw new EnmException(String.format(failed, responseCode), EnmErrorType.SYSTEM);
        }
    }

    public boolean isLoginSuccess() {
        CommonUtils.sleep(1);
        try {
            login();
        } catch(Exception e) {
            logger.info("isLoginSuccess() : false, {}", e.getMessage());
            return false;
        }
        logger.info("isLoginSuccess() : {}", islogoutNeeded);
        return islogoutNeeded;
    }

    /**
     * Logs into ENM.
     *
     * @throws EnmException if login failed
     */
    public void login() throws EnmException {
        try {
            final List<String[]> bodies = new ArrayList<>();
            if(AdminUserUtils.getEnmFlag().equalsIgnoreCase("enm")) {
                bodies.add(new String[]{"IDToken1", HAPropertiesReader.getEnmUsername()});
                bodies.add(new String[]{"IDToken2", HAPropertiesReader.getEnmPassword()});
            } else if(AdminUserUtils.getEnmFlag().equalsIgnoreCase("ha")) {
                bodies.add(new String[]{"IDToken1", AdminUserUtils.getUser()});
                bodies.add(new String[]{"IDToken2", HAPropertiesReader.getHaPassword()});
            }

            final HttpResponse httpResponse = httpRestServiceClient.sendPostRequest(TIMEOUT_LOGIN_LOGOUT, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, bodies, null, "/login");
            verifyResponse(httpResponse, "Login Success", LOGIN_FAILED);
            if(httpResponse.getResponseCode().getCode() == 200) {
                islogoutNeeded = true;
            } else {
                logger.info("Login to ENM is Not Successful");
                islogoutNeeded = false;
            }
        } catch (final IllegalStateException e) {
            islogoutNeeded = false;
            logger.warn(String.format(LOGIN_FAILED, e.getMessage()), e);
            throw new EnmException(String.format(LOGIN_FAILED, e.getMessage()), EnmErrorType.SYSTEM);
        }
    }

    /**
     * Logs out from ENM.
     *
     * @throws EnmException if logout failed
     */
    public void logout() throws EnmException {
        if (islogoutNeeded) {
            try {
                final HttpResponse httpResponse = httpRestServiceClient.sendGetRequest(TIMEOUT_LOGIN_LOGOUT, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, "/logout");
                verifyResponse(httpResponse, "Logout Success", LOGOUT_FAILED_WITH_CODE);
            } catch (final IllegalStateException e) {
                logger.warn(String.format(LOGOUT_FAILED_WITH_CODE, e.getMessage()), e);
                throw new EnmException(String.format(LOGOUT_FAILED_WITH_CODE, e.getMessage()), EnmErrorType.SYSTEM);
            } finally {
                httpRestServiceClient.getHttpTool().clearCookies();
            }
        }else{
            logger.info("Logout is not performed as login is not success");
        }
    }

    /**
     * Verifies that an ENM web application, e.g. CM, FM, PM, etc, works fine. The default implementation does nothing.
     *
     * @throws EnmException     if a command cannot be executed
     * @throws TimeoutException if the command could not be executed within {@link HAPropertiesReader#getMaxDisruptionTime()}
     */
    public abstract void verify() throws EnmSystemException, EnmException, TimeoutException, HaTimeoutException;

    public void prepare() throws Exception {
    }

    public void cleanup() throws Exception {
    }

    public void analyzeResponse(final String errMessage, final HttpResponse response) throws EnmException {
        final Map<String, String> headers = response.getHeaders();
        final String authErrorCode = headers.get("X-AuthErrorCode");
        final HttpStatus responseCode = response.getResponseCode();

        stopAppTimeoutDownTime(response.getResponseTimeMillis());

        // TORRV-6177 (only failures during login/logout are SYSTEM Downtime)
        final EnmErrorType enmErrorType = EnmErrorType.APPLICATION;

        if (!acceptableResponse.contains(responseCode) && !AUTH_CODE_SUCCESS.equals(authErrorCode)) {
            logger.warn(errMessage);

            logger.error("(response code: {})", responseCode);
            final String responseBody = response.getBody();
            if (StringUtils.isNotEmpty(responseBody)) {
                if (responseBody.length() > 200) {
                    final String msg = responseBody.substring(0, 200).trim();
                    logger.error("Response's body: {}", msg);
                } else {
                    final String msg = responseBody.trim();
                    logger.error("Response's body: {}", msg);
                }
            }

            throw new EnmException(errMessage + " - (response code: " + responseCode + ')', enmErrorType);
        } else {
            stopAppDownTime(response.getResponseTimeMillis());
            setFirstTimeFail(true);
        }
    }

    public void analyzeResponseWithoutStopAppDownTime(final String errMessage, final HttpResponse response) throws EnmException {
        final Map<String, String> headers = response.getHeaders();
        final String authErrorCode = headers.get("X-AuthErrorCode");
        final HttpStatus responseCode = response.getResponseCode();

        // TORRV-6177 (only failures during login/logout are SYSTEM Downtime)
        final EnmErrorType enmErrorType = EnmErrorType.APPLICATION;

        if (!acceptableResponse.contains(responseCode) && !AUTH_CODE_SUCCESS.equals(authErrorCode)) {
            logger.warn(errMessage);

            logger.error("(response code: {})", responseCode);
            final String responseBody = response.getBody();
            if (StringUtils.isNotEmpty(responseBody)) {
                if (responseBody.length() > 200) {
                    final String msg = responseBody.substring(0, 200).trim();
                    logger.error("Response's body: {}", msg);
                } else {
                    final String msg = responseBody.trim();
                    logger.error("Response's body: {}", msg);
                }
            }
            throw new EnmException(errMessage + " - (response code: " + responseCode + ')', enmErrorType);
        }
    }

    public void sleep(final long timeInSeconds) {
        try {
            Thread.sleep(timeInSeconds * (long) Constants.TEN_EXP_3);
        } catch (final InterruptedException ignore) {
            logger.warn("Failed to sleep.", ignore);
        }
    }

    public void startDowntime(final EnmErrorType enmErrorType) {
        switch (enmErrorType) {
            case APPLICATION:
                stopAppTimeoutDownTime();
                startAppDownTime();
                break;
            case SYSTEM:
                startSystemDownTime();
                break;
            default:
                logger.warn("Invalid Error Type: {}", enmErrorType.toString());
        }
    }

    public boolean isFirstTimeFail() {
        final AtomicBoolean atomicBoolean = firstTimeFail.get(clazz);
        if (atomicBoolean != null) {
            return atomicBoolean.get();
        } else {
            return false;
        }
    }

    public void setFirstTimeFail(final boolean firstTime) {
        final AtomicBoolean atomicBoolean = firstTimeFail.get(clazz);
        if (atomicBoolean != null) {
            atomicBoolean.set(firstTime);
        }
    }

    public SystemStatus getSystemStatus() {
        return systemStatus;
    }

    @Override
    public void doLogin() {
        isLoginNeed = true;
    }

    public boolean isLoginNeed() {
        return isLoginNeed;
    }

    public void cleanLoginFailEvent() {
        isLoginNeed = false;
    }

    public enum Header {

        /**
         * the {@code Accept} header
         */
        ACCEPT("Accept"),

        /**
         * the {@code CommandStatus} header
         */
        COMMAND_STATUS("CommandStatus"),

        /**
         * the {@code ResponseSize} header
         */
        RESPONSE_SIZE("ResponseSize");

        private final String value;

        Header(final String name) {
            this.value = name;
        }

        public String getValue() {
            return value;
        }
    }
}
