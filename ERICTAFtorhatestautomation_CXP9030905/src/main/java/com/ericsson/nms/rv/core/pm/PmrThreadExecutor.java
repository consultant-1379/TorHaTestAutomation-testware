package com.ericsson.nms.rv.core.pm;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static com.ericsson.nms.rv.core.HAPropertiesReader.ignoreDTMap;
import static com.ericsson.nms.rv.core.pm.PerformanceManagementRouter.pmrIgnoreDTMap;
import static com.ericsson.nms.rv.taf.tools.Constants.EMPTY_STRING;

public class PmrThreadExecutor implements Runnable {

    private static final Logger logger = LogManager.getLogger(PmrThreadExecutor.class);
    private static final String ACTIVATE = "activate";
    private static final String DEACTIVATE = "deactivate";
    private static final String CREATE = "create";
    private static final String RETRIEVE = "retrieve";
    private static final String REST_SUBSCRIPTION_URL = "pm-service/rest/subscription/";
    private static final String MESSAGE = "Failed to %s subscription %s";
    private static final String FAILMESSAGE = "Failed to %s some subscriptions ";
    private static final String FOR_ACTION_TYPE_POST_AND_ACTION = "For ActionType (POST) and action (";
    private static final String HTTP_RESPONSE_IS_NULL = ") HttpResponse is NULL!";
    private static final String FAILED_TO_READ = "Failed to read ";
    private Thread worker;
    private HttpRestServiceClient httpRestClient;
    private boolean completed;
    private boolean ErrorOccurance;
    private boolean timedOut;
    private EnmApplication enmApplication;
    private String uniqueSubscriptionName;
    private JSONArray syncedManagedObjects;
    private List<Node> nodes;


    public boolean isCompleted() {
        return this.completed;
    }

    public boolean isErrorOcurred() {
        return this.ErrorOccurance;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public static Logger getLogger() {
        return logger;
    }

    public long getID() {
        return this.worker.getId();
    }

    public void execute() {
        this.worker = new Thread(this);
        this.worker.start();
    }

    public PmrThreadExecutor(final HttpRestServiceClient httpRestServiceClient, final EnmApplication application, final String uniqueSubscriptionName, final JSONArray syncedManagedObjects, final List<Node> nodes) {
        this.httpRestClient = httpRestServiceClient;
        this.enmApplication = application;
        this.uniqueSubscriptionName = uniqueSubscriptionName;
        this.syncedManagedObjects = syncedManagedObjects;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        try {
            pmicFlow(uniqueSubscriptionName);
            this.completed = true;
        } catch (final EnmException e) {
            logger.warn("thread id {} : {}",this.worker.getId(),e.getStackTrace());
            this.ErrorOccurance = true;
            if(this.enmApplication.isFirstTimeFail()){
                this.enmApplication.setFirstTimeFail(false);
            }else{
                this.enmApplication.stopAppTimeoutDownTime();
                this.enmApplication.startAppDownTime();
            }
        } catch (final HaTimeoutException e) {
            logger.warn("thread id {} : {}",this.worker.getId(),e.getStackTrace());
            this.timedOut = true;
        } catch (final Exception e) {
            logger.warn("thread id {} : {}",this.worker.getId(),e.getStackTrace());
            this.ErrorOccurance = true;
            if(this.enmApplication.isFirstTimeFail()){
                this.enmApplication.setFirstTimeFail(false);
            }else{
                this.enmApplication.stopAppTimeoutDownTime();
                this.enmApplication.startAppDownTime();
            }
        } finally {
            CommonUtils.sleep(1);
            Thread.currentThread().stop();
        }
    }

    private void pmicFlow(final String subscriptionName) throws EnmException, HaTimeoutException {
        try {
            final String msg = String.format(MESSAGE, CREATE, subscriptionName);
            final String subscriptionId = createSubscription(subscriptionName, syncedManagedObjects, SubscriptionType.STATISTICAL);
            if (subscriptionId != null && !subscriptionId.isEmpty()) {
                activateSubscription(subscriptionId);
                deactivateSubscription(subscriptionId);
                deleteSubscription(subscriptionId);
            } else {
                logger.warn("Thread id {} : {} subscription id is Empty", this.worker.getId(), msg);
                throw new EnmException("subscription id is Empty");
            }
        } catch (IllegalStateException e){
            logger.warn("Thread id {} : time out in PMR Flow",this.worker.getId());
            throw new HaTimeoutException(e.getMessage());
        } catch (final Exception e) {
            logger.warn("Thread id {} error ocurred in PMR Flow {}",this.worker.getId(), e.getMessage());
            throw new EnmException("error ocurred in PMR Flow" + e.getMessage());
        }
    }

    /**
     * Creates a PMIC subscription.
     *
     * @param nodes JSON array containing the MO's which will be in the subscription
     * @return the subscription's id
     * @throws EnmException if failed to create the subscription
     */
    private String createSubscription(final String subscriptionName, final JSONArray nodes, final SubscriptionType subscriptionType) throws EnmException, IllegalStateException {
        final JSONArray counterEventInfo = getCounterEventInfo((JSONObject) syncedManagedObjects.get(0), SubscriptionType.STATISTICAL);
        String jsonFile = null;
        if (SubscriptionType.STATISTICAL.equals(subscriptionType)) {
            jsonFile = "/json/pmicJson.json";
        }
        List<String[]> bodies;
        try (final InputStream inputStream = PmrThreadExecutor.class.getResourceAsStream(jsonFile)) {
            if (!counterEventInfo.isEmpty()) {
                bodies = fillBody(inputStream, nodes, subscriptionName, Constants.SUBSCRIPTION_DESCRIPTION, counterEventInfo, subscriptionType, jsonFile);
                if (bodies.isEmpty()) {
                    return Constants.EMPTY_STRING;
                }
            } else {
                return Constants.EMPTY_STRING;
            }
        } catch (final IOException e) {
            throw new EnmException(FAILED_TO_READ + jsonFile + ", root cause: " + e, EnmErrorType.APPLICATION);
        }
        HttpResponse response = sendPostRequest(subscriptionName, bodies);
        if (response != null) {
            enmApplication.analyzeResponseWithoutStopAppDownTime(String.format(MESSAGE, CREATE, subscriptionName), response);
            Object parseBody = JSONValue.parse(response.getBody());
            if (parseBody instanceof JSONObject) {
                final String url = (String) ((JSONObject) parseBody).get("url");
                final HttpStatus responseCode = response.getResponseCode();
                if (url != null || !HttpStatus.CREATED.equals(responseCode)) {
                    final long timeOut = HAPropertiesReader.getPmTimeToWaitForSubscription();
                    final long startTime = System.nanoTime();
                    do {
                        final long reqStartTime = System.currentTimeMillis();
                        Date startDate = new Date(reqStartTime);
                        try {
                            response = httpRestClient.sendGetRequest(HAPropertiesReader.pmRestTimeOut, ContentType.APPLICATION_JSON,
                                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, url);
                        } catch (final IllegalStateException var14) {
                            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, CREATE, subscriptionName) + ": " + var14.getMessage(), EnmErrorType.APPLICATION);
                            this.enmApplication.setFirstTimeFail(true);
                            this.enmApplication.stopAppDownTime();
                            this.enmApplication.startAppTimeoutDowntime();
                            this.timedOut = true;
                            response = httpRestClient.sendGetRequest(100, ContentType.APPLICATION_JSON,
                                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, url);
                            long respTime = response.getResponseTimeMillis() + 20000L;
                            if (response != null && this.checkResponse(response) && respTime < 120000L) {
                                logger.info("thread id {} Delayed Response time: {} ...... ", this.worker.getId() ,respTime);
                                Date endDate = new Date(reqStartTime + respTime);
                                pmrIgnoreDTMap.put(startDate, endDate);
                                logger.info("Thread id {} pmrIgnoreDTMap : .........{} ",this.worker.getId(), pmrIgnoreDTMap);
                                ignoreDTMap.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, pmrIgnoreDTMap);
                            }
                        } catch (final Exception var15) {
                            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, CREATE, subscriptionName) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
                            throw new EnmException(String.format(MESSAGE, CREATE, subscriptionName) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
                        }
                        if (response != null) {
                            enmApplication.analyzeResponse(String.format(MESSAGE, CREATE, subscriptionName), response);
                            if (enmApplication.sleepAndCatchInterruptedException()) {
                                break;
                            }
                        } else {
                            logger.warn("thread id {} : {}", this.worker.getId() , String.format(MESSAGE, CREATE, subscriptionName));
                            throw new EnmException(String.format(MESSAGE, CREATE, subscriptionName)+" response is NULL");
                        }
                    } while (!EnmApplication.getSignal() && System.nanoTime() - startTime < timeOut * Constants.TEN_EXP_9 && !(HttpStatus.OK.equals(response.getResponseCode())));

                    if (System.nanoTime() - startTime <= timeOut * Constants.TEN_EXP_9) {
                        parseBody = JSONValue.parse(response.getBody());
                        if (parseBody instanceof JSONObject) {
                            final String subscriptionId = (String) ((JSONObject) parseBody).get("id");
                            logger.info("Thread id {} Subscription {} created with id: {}",this.worker.getId(), subscriptionName, subscriptionId);
                            return subscriptionId;
                        } else {
                            throw new EnmException(String.format(MESSAGE, CREATE, subscriptionName), EnmErrorType.APPLICATION);
                        }
                    } else {
                        throw new EnmException("Failed to retrieve subscription", EnmErrorType.APPLICATION);
                    }
                } else {
                    //  TORF-155923
                    final String id = (String) ((JSONObject) parseBody).get("id");
                    if (id != null && HttpStatus.CREATED.equals(responseCode)) {
                        logger.info("Thread id {} Subscription {} created with id: {}",this.worker.getId(), subscriptionName, id);
                        return id;
                    } else {
                        final String message = "Failed to send POST request, URL is NULL";
                        throw new EnmException(message, EnmErrorType.APPLICATION);
                    }
                }
            } else {
                throw new EnmException(String.format(MESSAGE, CREATE, subscriptionName), EnmErrorType.APPLICATION);
            }
        } else {
            logger.warn("Thread id {} For ActionType (POST) and action ({}) HttpResponse is NULL!",this.worker.getId(), REST_SUBSCRIPTION_URL);
            return Constants.EMPTY_STRING;
        }

    }

    private List<String[]> fillBody(final InputStream inputStream, final JSONArray nodes, final String name, final String description,
                                    final JSONArray counterEventInfo, final SubscriptionType subscriptionType, final String jsonFile) {
        final Object parse = JSONValue.parse(inputStream);
        if (parse instanceof JSONObject) {
            final JSONObject jsonObject = (JSONObject) parse;
            jsonObject.put("nodes", nodes);
            jsonObject.put("name", name);
            jsonObject.put("description", description);
            if (SubscriptionType.STATISTICAL.equals(subscriptionType)) {
                jsonObject.put("counters", counterEventInfo);
            }
            final List<String[]> bodies = new ArrayList<>();
            bodies.add(new String[]{jsonObject.toString()});
            return bodies;
        } else {
            final String msg = FAILED_TO_READ + jsonFile;
            logger.warn("Thread id {} : {} ",this.worker.getId(),msg);
            return new ArrayList<>();
        }
    }

    private HttpResponse sendPostRequest(final String name, final List<String[]> bodies) throws EnmException, IllegalStateException {
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        HttpResponse response;
        try {
            response = httpRestClient.sendPostRequest(HAPropertiesReader.pmRestTimeOut, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, REST_SUBSCRIPTION_URL);
        } catch (final IllegalStateException var14) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, CREATE, name) + ": " + var14.getMessage(), EnmErrorType.APPLICATION);
            this.enmApplication.setFirstTimeFail(true);
            this.enmApplication.stopAppDownTime();
            this.enmApplication.startAppTimeoutDowntime();
            this.timedOut = true;
            response = httpRestClient.sendPostRequest(100, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, REST_SUBSCRIPTION_URL);
            long respTime = response.getResponseTimeMillis() + 20000L;
            if (response != null && this.checkResponse(response) && respTime < 120000L) {
                logger.info("thread id {} Delayed Response time: {} ...... ", this.worker.getId() ,respTime);
                Date endDate = new Date(startTime + respTime);
                pmrIgnoreDTMap.put(startDate, endDate);
                logger.info("Thread id {} pmrIgnoreDTMap : .........{} ",this.worker.getId(), pmrIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, pmrIgnoreDTMap);
            }
        } catch (final Exception var15) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, CREATE, name) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
            throw new EnmException(String.format(MESSAGE, CREATE, name) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
        }
        return response;
    }

    /**
     * Delete subscription with the given subscriptionId.
     *
     * @throws EnmException if failed to delete the subscription
     */
    private void deleteSubscription(final String subscriptionId) throws EnmException, IllegalStateException {
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        HttpResponse response;
        try {
            response = httpRestClient.sendDeleteRequest(HAPropertiesReader.pmRestTimeOut, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, REST_SUBSCRIPTION_URL + subscriptionId);
        } catch (final IllegalStateException var14) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, "delete", subscriptionId) + ": " + var14.getMessage(), EnmErrorType.APPLICATION);
            this.enmApplication.setFirstTimeFail(true);
            this.enmApplication.stopAppDownTime();
            this.enmApplication.startAppTimeoutDowntime();
            this.timedOut = true;
            response = httpRestClient.sendDeleteRequest(100, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, REST_SUBSCRIPTION_URL + subscriptionId);
            long respTime = response.getResponseTimeMillis() + 20000L;
            if (response != null && this.checkResponse(response) && respTime < 120000L) {
                logger.info("thread id {} Delayed Response time: {} ...... ", this.worker.getId() ,respTime);
                Date endDate = new Date(startTime + respTime);
                pmrIgnoreDTMap.put(startDate, endDate);
                logger.info("Thread id {} pmrIgnoreDTMap : .........{} ",this.worker.getId(), pmrIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, pmrIgnoreDTMap);
            }
        } catch (final Exception var15) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, "delete", subscriptionId) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
            throw new EnmException(String.format(MESSAGE, "delete", subscriptionId) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
        }
        if (response != null) {
            enmApplication.analyzeResponseWithoutStopAppDownTime(String.format(MESSAGE, "delete", subscriptionId), response);
            logger.info("Thread id {} The subscription {} has been deleted",this.worker.getId(), subscriptionId);
        } else {
            logger.warn("Thread id {} For ActionType (POST) and action ({}{}) HttpResponse is NULL!",this.worker.getId(), REST_SUBSCRIPTION_URL, subscriptionId);
            throw new EnmException("Failed to delete subscription "+subscriptionId+ ", HttpResponse is NULL");
        }
    }

    /**
     * Returns the counter event information for a subscription as a JSON array.
     */
    private JSONArray getCounterEventInfo(final JSONObject jsonObject, final SubscriptionType subscriptionType) throws EnmException, IllegalStateException {
        final JSONArray counterEventInfo = new JSONArray();
        final String neType = (String) jsonObject.get("neType");
        final String ossModelIdentity = (String) jsonObject.get("ossModelIdentity");
        final String getCounterEventInfo = "pm-service/rest/pmsubscription/%smim=%s:%s";
        String request = EMPTY_STRING;
        if (SubscriptionType.STATISTICAL.equals(subscriptionType)) {
            request = String.format(getCounterEventInfo, "counters?definer=NE&", neType, ossModelIdentity);
        }
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        HttpResponse response;
        try {
            response = httpRestClient
                    .sendGetRequest(HAPropertiesReader.pmRestTimeOut, null, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null,
                            request);
        } catch (final IllegalStateException var14) {
            logger.warn("Thread id {} Failed to retrieve CounterEventInfo" + ": " + var14.getMessage(),this.worker.getId(), EnmErrorType.APPLICATION);
            this.enmApplication.setFirstTimeFail(true);
            this.enmApplication.stopAppDownTime();
            this.enmApplication.startAppTimeoutDowntime();
            this.timedOut = true;
            response = httpRestClient
                    .sendGetRequest(100, null, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null,
                            request);
            long respTime = response.getResponseTimeMillis() + 20000L;
            if (response != null && this.checkResponse(response) && respTime < 120000L) {
                logger.info("thread id {} Delayed Response time: {} ...... ", this.worker.getId() ,respTime);
                Date endDate = new Date(startTime + respTime);
                pmrIgnoreDTMap.put(startDate, endDate);
                logger.info("Thread id {} pmrIgnoreDTMap : .........{} ",this.worker.getId(), pmrIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, pmrIgnoreDTMap);
            }
        } catch (final Exception var15) {
            this.enmApplication.startAppDownTime();
            this.ErrorOccurance = true;
            logger.warn("Thread id {} Failed to retrieve CounterEventInfo .........{} ",this.worker.getId(), var15.getMessage());
            throw new EnmException("Failed to retrieve CounterEventInfo " + var15.getMessage());
        }
        if (response != null) {
            final Object parse = JSONValue.parse(response.getBody());
            if (parse instanceof JSONArray) {
                final JSONArray jsonArray = (JSONArray) parse;
                final Random random = new Random(System.currentTimeMillis());
                final JSONObject jsonObj = (JSONObject) jsonArray.get(random.nextInt(jsonArray.size()));
                final JSONObject object = new JSONObject();
                if (SubscriptionType.STATISTICAL.equals(subscriptionType)) {
                    object.put("name", jsonObj.get("counterName"));
                    object.put("moClassType", jsonObj.get("sourceObject"));
                }
                counterEventInfo.add(object);
                return counterEventInfo;
            } else {
                logger.warn("Thread id {} Failed to read CounterEventInfo response",this.worker.getId());
            }
        } else {
            logger.warn("Thread id {} Failed to read CounterEventInfo, HttpResponse is NULL",this.worker.getId());
        }
        return counterEventInfo;
    }

    /**
     * Returns the {@code administrationState} of the given subscription.
     *
     * @throws java.lang.IllegalStateException if the {@code administrationState} is not one of {@link AdministrationState}
     * @throws EnmException                    if failed to get the {@code administrationState}
     */
    private AdministrationState getSubscriptionAdministrationState(final String retrieveSubscription) {
        final Object parse = JSONValue.parse(retrieveSubscription);
        if (parse instanceof JSONObject) {
            final JSONObject jsonObject = (JSONObject) parse;
            final String state = (String) jsonObject.get("administrationState");
            try {
                return AdministrationState.valueOf(state.toUpperCase());
            } catch (final IllegalArgumentException e) {
                throw new IllegalStateException(state, e);
            }
        } else {
            return AdministrationState.EMPTY;
        }
    }

    private TaskStatus getSubscriptionTaskStatus(final String retrieveSubscription) {
        final Object parse = JSONValue.parse(retrieveSubscription);
        if (parse instanceof JSONObject) {
            final JSONObject jsonObject = (JSONObject) parse;
            final String state = (String) jsonObject.get("taskStatus");
            try {
                return TaskStatus.valueOf(state.toUpperCase());
            } catch (final IllegalArgumentException e) {
                throw new IllegalStateException(state, e);
            }
        } else {
            return TaskStatus.EMPTY;
        }
    }

    /**
     * Retrieves the subscription with the given subscriptionId.
     *
     * @return the subscription details as a JSON string
     * @throws EnmException if failed to retrieve the subscription
     */
    private String retrieveSubscription(final String subscriptionId) throws EnmException, IllegalStateException {
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        HttpResponse response;
        try {
            response = httpRestClient
                    .sendGetRequest(HAPropertiesReader.pmRestTimeOut, null, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null,
                            REST_SUBSCRIPTION_URL + subscriptionId);
        } catch (final IllegalStateException var14) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, RETRIEVE, subscriptionId) + ": " + var14.getMessage(), EnmErrorType.APPLICATION);
            this.enmApplication.setFirstTimeFail(true);
            this.enmApplication.stopAppDownTime();
            this.enmApplication.startAppTimeoutDowntime();
            this.timedOut = true;
            response = httpRestClient
                    .sendGetRequest(100, null, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null,
                            REST_SUBSCRIPTION_URL + subscriptionId);
            long respTime = response.getResponseTimeMillis() + 20000L;
            if (response != null && this.checkResponse(response) && respTime < 120000L) {
                logger.info("thread id {} Delayed Response time: {} ...... ", this.worker.getId() ,respTime);
                Date endDate = new Date(startTime + respTime);
                pmrIgnoreDTMap.put(startDate, endDate);
                logger.info("Thread id {} pmrIgnoreDTMap : .........{} ",this.worker.getId(), pmrIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, pmrIgnoreDTMap);
            }
        } catch (final Exception var15) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, RETRIEVE, subscriptionId) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
            throw new EnmException(String.format(MESSAGE, RETRIEVE, subscriptionId) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
        }
        if (response != null) {
            enmApplication.analyzeResponse(String.format(MESSAGE, RETRIEVE, subscriptionId), response);
            return response.getBody();
        } else {
            final String msg = FOR_ACTION_TYPE_POST_AND_ACTION + REST_SUBSCRIPTION_URL + subscriptionId + HTTP_RESPONSE_IS_NULL;
            logger.warn("Thread id {} : {} ",this.worker.getId(),msg);
            return Constants.EMPTY_STRING;
        }
    }

    private boolean activateSubscription(final String subscriptionId) throws EnmException, IllegalStateException {
        return activateSubscription(true, subscriptionId);
    }

    private boolean deactivateSubscription(final String subscriptionId) throws EnmException, IllegalStateException {
        return activateSubscription(false, subscriptionId);
    }

    /**
     * Activates or deactivates the subscription with the given subscriptionId.
     *
     * @param activate {@code true} if the subscription should be activated, or {@code false} for deactivation
     * @throws EnmException if failed to activate or deactivate within the given time
     */
    private boolean activateSubscription(final boolean activate, final String subscriptionId) throws EnmException, IllegalStateException {
        final String activateSubscriptionUrl = REST_SUBSCRIPTION_URL + "%s/" + ACTIVATE;
        final String deactivateSubscriptionUrl = REST_SUBSCRIPTION_URL + "%s/" + DEACTIVATE;
        final String msg = activate ? ACTIVATE : DEACTIVATE;
        // get the subscription's persistence time
        final Object parse = JSONValue.parse(retrieveSubscription(subscriptionId));
        long persistenceTime = 0;
        if (parse instanceof JSONObject) {
            persistenceTime = (Long) ((JSONObject) parse).get("persistenceTime");
        }
        // activate or deactivate the subscription
        final JSONObject jsonObject = new JSONObject();
        jsonObject.put("persistenceTime", persistenceTime);
        final String post = String.format(activate ? activateSubscriptionUrl : deactivateSubscriptionUrl, subscriptionId);
        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});
        final long startTime = System.nanoTime();
        final long reqstartTime = System.currentTimeMillis();
        Date startDate = new Date(reqstartTime);
        HttpResponse response;
        try {
            response = httpRestClient.sendPostRequest(HAPropertiesReader.pmRestTimeOut, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, post);
        } catch (final IllegalStateException var14) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, msg, subscriptionId) + ": " + var14.getMessage(), EnmErrorType.APPLICATION);
            this.enmApplication.setFirstTimeFail(true);
            this.enmApplication.stopAppDownTime();
            this.enmApplication.startAppTimeoutDowntime();
            this.timedOut = true;
            response = httpRestClient.sendPostRequest(100, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, post);
            long respTime = response.getResponseTimeMillis() + 20000L;
            if (response != null && this.checkResponse(response) && respTime < 120000L) {
                logger.info("thread id {} Delayed Response time: {} ...... ", this.worker.getId() ,respTime);
                Date endDate = new Date(reqstartTime + respTime);
                pmrIgnoreDTMap.put(startDate, endDate);
                logger.info("Thread id {} pmrIgnoreDTMap : .........{} ",this.worker.getId(), pmrIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, pmrIgnoreDTMap);
            }
        } catch (final Exception var15) {
            logger.warn("Thread id {} : {} ",this.worker.getId(),String.format(MESSAGE, msg, subscriptionId) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
            throw new EnmException(String.format(MESSAGE, msg, subscriptionId) + ": " + var15.getMessage(), EnmErrorType.APPLICATION);
        }
        if (response != null) {
            enmApplication.analyzeResponseWithoutStopAppDownTime(String.format(MESSAGE, msg, subscriptionId), response);
        } else {
            logger.warn("Thread id {} For ActionType (POST) and action ({}) HttpResponse is NULL!",this.worker.getId(), post);
            throw new EnmException("Thread id "+this.worker.getId()+" HttpResponse is NULL");
        }
        // wait for the subscription to become active / inactive
        AdministrationState administrationState = activate ? AdministrationState.INACTIVE : AdministrationState.ACTIVE;
        final AdministrationState targetState = activate ? AdministrationState.ACTIVE : AdministrationState.INACTIVE;
        TaskStatus taskStatus = TaskStatus.OK;
        final long loopStartTime = System.nanoTime();
        while (administrationState != targetState && TaskStatus.OK.equals(taskStatus) && (System.nanoTime() - loopStartTime < 600L * Constants.TEN_EXP_9)) {
            try {
                final String retrieveSubscription = retrieveSubscription(subscriptionId);
                administrationState = getSubscriptionAdministrationState(retrieveSubscription);
                taskStatus = getSubscriptionTaskStatus(retrieveSubscription);
                logger.debug("Thread id {} administrationState={}; taskStatus={}",this.worker.getId(), administrationState, taskStatus);
            } catch (final IllegalStateException ignore) {
                logger.warn("Thread id {} Failed to getSubscriptionAdministrationState.",this.worker.getId(), ignore);
            } finally {
                enmApplication.sleep(1L);
            }
        }
        if (System.nanoTime() - loopStartTime > 600L * (long) Constants.TEN_EXP_9) {
            if (activate) {
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, String.format(FAILMESSAGE, ACTIVATE));
            } else {
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.PERFORMANCEMANAGEMENTROUTER, String.format(FAILMESSAGE, DEACTIVATE));
            }
        }
        logger.info("Thread id {} execution time of {} is : {}",this.worker.getId(), msg, (System.nanoTime() - startTime) / Constants.TEN_EXP_9);
        boolean ret;
        ret = TaskStatus.OK.equals(taskStatus);
        if (activate) {
            if (AdministrationState.ACTIVE.equals(administrationState)) {
                if (ret) {
                    logger.info("Thread id {} The subscription {} has been activated",this.worker.getId(), subscriptionId);
                } else {
                    final String msgActivate = String.format(MESSAGE, ACTIVATE, subscriptionId);
                    logger.warn("Thread id {} : {} ",this.worker.getId(),msgActivate);
                    throw new EnmException(msgActivate);
                }
            } else {
                final String msgActivate = String.format(MESSAGE, ACTIVATE, subscriptionId);
                logger.warn("Thread id {} : {} ",this.worker.getId(),msgActivate);
                throw new EnmException(msgActivate);
            }
        } else {
            if (AdministrationState.INACTIVE.equals(administrationState)) {
                if (ret) {
                    logger.info("Thread id {} The subscription {} has been deactivated",this.worker.getId(), subscriptionId);
                } else {
                    final String msgDeactivate = String.format(MESSAGE, DEACTIVATE, subscriptionId);
                    logger.warn("Thread id {} : {} ",this.worker.getId(),msgDeactivate);
                    throw new EnmException(msgDeactivate);
                }
            } else {
                final String msgDeactivate = String.format(MESSAGE, DEACTIVATE, subscriptionId);
                logger.warn("Thread id {} : {} ",this.worker.getId(),msgDeactivate);
                throw new EnmException(msgDeactivate);
            }
        }
        return ret;
    }

    private enum SubscriptionType {
        STATISTICAL
    }

    @SuppressWarnings("unused")
    private enum AdministrationState {
        ACTIVE, ACTIVATING, INACTIVE, DEACTIVATING, UPDATING, SCHEDULED,EMPTY
    }

    @SuppressWarnings("unused")
    private enum TaskStatus {
        ERROR, OK, NA,EMPTY
    }

    private boolean checkResponse(HttpResponse response) throws IllegalStateException {
        if (EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            this.enmApplication.setFirstTimeFail(true);
            this.enmApplication.stopAppTimeoutDownTime();
            this.enmApplication.stopAppDownTime();
            return true;
        } else if (response.getResponseCode().getCode() == 504) {
            throw new IllegalStateException(response.getResponseCode().toString());
        } else {
            return false;
        }
    }
}
