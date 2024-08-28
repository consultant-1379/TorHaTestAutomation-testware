package com.ericsson.nms.rv.core.netex;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

/**
 * {@code NetworkExplorer} allows to run Network Explorer queries.
 */
public final class NetworkExplorer extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(NetworkExplorer.class);
    /**
     * the URL for querying the managed objects
     */
    private static final String QUERY_MANAGED_OBJECTS_URL = "/managedObjects/query/";
    private static final String QUERY_COLLECTION_URL = "/object-configuration/v1/collections/";
    private static final String FOR_ACTION_TYPE_POST_AND_ACTION_HTTP_RESPONSE_IS_NULL = "For ActionType (POST) and action ({}) HttpResponse is NULL!";
    private static final String HA_COLLECTION = "ha-test-collection";
    private static final List<Node> NODE_LIST = LoadGenerator.getNodes();
    private static final String FAILED_TO_QUERY = "Failed to query '";
    private static final String FAILED_TO_QUERY_COLLECTIONS = "Failed to query collections";
    private static final String GET = "GET: ";
    private boolean ready;
    private String collectionId;
    public static NavigableMap<Date, Date> netExIgnoreDTMap = new ConcurrentSkipListMap();

    public NetworkExplorer(final boolean initDownTimerHandler, final SystemStatus systemStatus) {
        if (initDownTimerHandler) {
            initDownTimerHandler(systemStatus);
            initFirstTimeFail();
        }
    }

    @Override
    public void prepare() {
        if (!NODE_LIST.isEmpty()) {
            final String collections;
            final String haCollectionId;

            try {
                collections = queryCollections();
                haCollectionId = getCollectionId(collections);

                if (!StringUtils.isEmpty(haCollectionId)) {
                    deleteCollection(haCollectionId);
                }
                int i = 0;
                do {
                    logger.info("Preparing NETEX... attempt {}", ++i);
                    final Random random = new Random(System.currentTimeMillis());
                    final String managedObjects = queryManagedObjects(NODE_LIST.get(random.nextInt(NODE_LIST.size())).getNetworkElementId());
                    final String poId = getPoId(managedObjects);
                    collectionId = createCollection(poId);
                    ready = StringUtils.isNoneEmpty(collectionId);
                } while (!ready && i < 3);

            } catch (final EnmException | HaTimeoutException e) {
                logger.info("Preparing NETEX Error...", e);
            } finally {
                logger.info("Is it ready to Verify? {}", ready);
                if (!ready) {
                    HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get("netex"), HAPropertiesReader.appMap.get("netex") + ", Failed in Prepare.");
                    HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(NetworkExplorer.class.getSimpleName(), true));
                }
            }
        } else {
            logger.info("Preparing NETEX Error... Node list empty");
        }

    }

    @Override
    public void cleanup() throws HaTimeoutException{
        try {
            deleteCollection(collectionId);
        } catch (final EnmException e) {
            logger.info("Cleaning NETEX Error...", e);
        }
    }

    @Override
    public void verify() throws EnmException, HaTimeoutException {
        if (ready) {
            getQueryCollectionsByIdAsync(collectionId);
            logger.info("NETEX Running... ");
        }
    }

    /**
     * Queries managed objects.
     *
     * @param managedObject the {@link ManagedObject} to be queried
     * @return the HTTP response's body from the REST call
     * @throws NetworkExplorerException if failed to query the managed object
     */
    public String queryManagedObjects(final ManagedObject managedObject) throws NetworkExplorerException, HaTimeoutException {
        try {
            return queryManagedObjects(managedObject.toString());
        } catch (final EnmException e) {
            throw new NetworkExplorerException("Error trying to get Managed Objects, " + e.getMessage(), e.getEnmErrorType(), e);
        }
    }

    /**
     * Queries managed objects.
     *
     * @param query the query
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String queryManagedObjects(final String query) throws EnmException, HaTimeoutException {
        final HttpResponse response;
        try {
            response = getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, null, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, new String[]{"searchQuery", query}, QUERY_MANAGED_OBJECTS_URL);

            if (response != null) {
                logResponseCode(response, GET + QUERY_MANAGED_OBJECTS_URL);
                analyzeResponse(FAILED_TO_QUERY + query + '\'', response);
                return response.getBody();
            } else {
                logger.warn(FOR_ACTION_TYPE_POST_AND_ACTION_HTTP_RESPONSE_IS_NULL, QUERY_MANAGED_OBJECTS_URL);
                return Constants.EMPTY_STRING;
            }
        } catch (final IllegalStateException e) {
            throw new HaTimeoutException(FAILED_TO_QUERY + query + '\'' + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    /**
     * Queries managed objects.
     *
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String queryCollections() throws EnmException, HaTimeoutException {
        final HttpResponse response;
        try {
            response = getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, null, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, QUERY_COLLECTION_URL);

            if (response != null) {
                logResponseCode(response, GET + QUERY_COLLECTION_URL);
                analyzeResponse(FAILED_TO_QUERY_COLLECTIONS, response);
                return response.getBody();
            } else {
                logger.warn(FOR_ACTION_TYPE_POST_AND_ACTION_HTTP_RESPONSE_IS_NULL, QUERY_COLLECTION_URL);
                return Constants.EMPTY_STRING;
            }
        } catch (final IllegalStateException e) {
            throw new HaTimeoutException(FAILED_TO_QUERY_COLLECTIONS  + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    private String getQueryCollectionsByIdAsync(final String colId) throws EnmException, HaTimeoutException {
        final String query = QUERY_COLLECTION_URL + colId;
        final long timeToWait = 20 * Constants.TEN_EXP_3;
        final long startTime = System.currentTimeMillis();
        HttpResponse response = null;
        NetExThreadExecutor thread = new NetExThreadExecutor(120, null, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, query, getHttpRestServiceClient());
        thread.execute();

        while (!(System.currentTimeMillis() - startTime >= timeToWait)) {
            sleep(1);
            response = thread.getResponse();
            if (response != null) {
                logger.info("Thread: {}, ResponseCode: {}, ResponseTime: {}", thread.getID(), response.getResponseCode().getCode(), response.getResponseTimeMillis());
                logResponseCode(response, GET + query);
                analyzeResponse(FAILED_TO_QUERY_COLLECTIONS, response);
                break;
            }
        }
        if(response == null) {      //No response after 20 sec.
            logger.warn("NetEx Timeout after 20 seconds.");
            throw new HaTimeoutException("NetEx Timeout after 20 seconds.");
        }
        logger.info("Active Thread counts : {}", java.lang.Thread.activeCount());
        return Constants.EMPTY_STRING;
    }

    /**
     * Queries managed objects.
     *
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String queryCollectionsById(final String colId) throws EnmException, HaTimeoutException {
        final HttpResponse response;
        try {
            final String query = QUERY_COLLECTION_URL + colId;
            response = getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, null, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, query);

            if (response != null) {
                logResponseCode(response, GET + query);
                analyzeResponse(FAILED_TO_QUERY_COLLECTIONS, response);
                return response.getBody();
            } else {
                logger.warn(FOR_ACTION_TYPE_POST_AND_ACTION_HTTP_RESPONSE_IS_NULL, query);
                throw new EnmException("Response is null !!");
            }
        } catch (final IllegalStateException e) {
            throw new HaTimeoutException(FAILED_TO_QUERY_COLLECTIONS + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    /**
     * Queries managed objects.
     *
     * @param ne the query
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String createCollection(final String ne) throws EnmException, HaTimeoutException {
        final HttpResponse response;

        final JSONObject object = new JSONObject();
        object.put("id", ne);

        final List<JSONObject> objects = new ArrayList<>();
        objects.add(object);

        final JSONObject params = new JSONObject();
        params.put("name", HA_COLLECTION);
        params.put("category", "Private");
        params.put("objects", objects);

        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{params.toString()});

        try {
            response = getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, bodies, null, QUERY_COLLECTION_URL);
            if (response != null) {
                logResponseCode(response, "POST: " + QUERY_COLLECTION_URL);
                analyzeResponse(FAILED_TO_QUERY + ne + '\'', response);

                final JSONObject params1 = (JSONObject) JSONValue.parse(response.getBody());
                return params1.getAsString("id");
            } else {
                logger.warn(FOR_ACTION_TYPE_POST_AND_ACTION_HTTP_RESPONSE_IS_NULL, QUERY_COLLECTION_URL);
                return Constants.EMPTY_STRING;
            }
        } catch (final IllegalStateException e) {
            throw new HaTimeoutException(FAILED_TO_QUERY + ne + '\'' + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    /**
     * Queries managed objects.
     *
     * @param colId the query
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String deleteCollection(final String colId) throws EnmException, HaTimeoutException {
        final HttpResponse response;

        try {
            final String query = QUERY_COLLECTION_URL + colId;
            response = getHttpRestServiceClient().sendDeleteRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, query);

            if (response != null) {
                logResponseCode(response, "DELETE: " + query);
                analyzeResponse(FAILED_TO_QUERY + colId + '\'', response);
                return response.getBody();
            } else {
                logger.warn(FOR_ACTION_TYPE_POST_AND_ACTION_HTTP_RESPONSE_IS_NULL, query);
                return Constants.EMPTY_STRING;
            }
        } catch (final IllegalStateException e) {
            throw new HaTimeoutException(FAILED_TO_QUERY + colId + '\'' + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    /**
     * Checks the attributes from a randomly selected MO.
     *
     * @throws EnmException if an error occurs executing a Network Explorer operation
     */
    private String getPoId(final String managedObjects) {
        final Object parse = JSONValue.parse(managedObjects);
        if (parse != null && parse instanceof JSONArray) {
            final JSONArray jsonArray = (JSONArray) parse;
            if (!jsonArray.isEmpty()) {
                final Object obj = jsonArray.get(0);
                if (obj instanceof JSONObject) {
                    return (String) ((JSONObject) obj).get("poId");
                } else {
                    logger.warn("Failed to check Mos!");
                }
            }
        } else {
            logger.warn("Failed to check Mos!");
        }
        return Constants.EMPTY_STRING;
    }

    /**
     * Checks the attributes from a randomly selected MO.
     *
     * @throws EnmException if an error occurs executing a Network Explorer operation
     */
    private String getCollectionId(final String collections) {
        final Object parse = JSONValue.parse(collections);

        if (parse != null && parse instanceof JSONObject) {

            final Object parseCollections = ((JSONObject) parse).get("collections");

            if (parseCollections != null && parseCollections instanceof JSONArray) {
                final JSONArray jsonArray = (JSONArray) parseCollections;

                for (int index = 0; index < jsonArray.size(); index++) {
                    final JSONObject col = (JSONObject) jsonArray.get(index);
                    final String name = (String) col.get("name");
                    if (HA_COLLECTION.equals(name)) {
                        return (String) col.get("id");
                    }
                }
            }
        } else {
            logger.warn("Failed to get Ha Collection Id!");
        }
        return Constants.EMPTY_STRING;
    }

    private void logResponseCode(final HttpResponse response, final String query) {
        logger.debug("response code for: ({}): {}", query, response.getResponseCode().getCode());
    }

    /**
     * the managed objects
     */
    public enum ManagedObject {

        ME_CONTEXT("MeContext"), NETWORK_ELEMENT("NetworkElement");

        private final String mo;

        ManagedObject(final String mo) {
            this.mo = mo;
        }

        @Override
        public String toString() {
            return mo;
        }
    }

}
