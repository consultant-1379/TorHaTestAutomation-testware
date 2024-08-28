package com.ericsson.nms.rv.core.cm.bulk;

import static com.ericsson.cifwk.taf.tools.http.constants.ContentType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.NavigableMap;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;


import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

public class CmImportToLive extends EnmApplication implements CmBulk {
    Logger logger = LogManager.getLogger(CmImportToLive.class);

    private static final String JOB_PREFIX = "HA_Test_Import_to_Live_";
    private static final String CM_LIVE_TIMEOUT_ERROR = "CM Import to Live Timeout error.";
    private final boolean readyToVerify;
    private final CmImportToLiveLoader cmImportToLiveLoader = new CmImportToLiveLoader();
    private final List<String> cmImportToLiveVerificationNodes = new ArrayList<>();
    private static List<String> subNetworkList = new ArrayList<>();
    private static String fileWriter;
    static NavigableMap<Date, Date> cmbImpToLiveIgnoreDTMap = new ConcurrentSkipListMap();

    public CmImportToLive(final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();

        final List<Node> liveVerificationNodes = cmImportToLiveLoader.getCmImportToLiveVerificationNodes();
        if (!liveVerificationNodes.isEmpty()) {
            cmImportToLiveVerificationNodes.addAll(liveVerificationNodes.stream().map(Node::getNetworkElementId).collect(Collectors.toList()));
        }
        logger.info("CM Import to Live Using nodes {}", getUsedNodes(liveVerificationNodes));
        readyToVerify = !cmImportToLiveVerificationNodes.isEmpty();
    }

    @Override
    public void prepare() throws EnmException {
        if (readyToVerify) {
            final String nodeType = cmImportToLiveLoader.getCmImportToLiveVerificationNodes().get(0).getNodeType();
            subNetworkList = getNodeSubNetwork(this, cmImportToLiveVerificationNodes.get(0));
            fileWriter = getImportFileWriter(subNetworkList, cmImportToLiveVerificationNodes.get(0), nodeType);
        }
    }

    @Override
    public void cleanup() throws EnmException, HaTimeoutException {
        cleanAllJobs();
    }

    @Override
    public void verify() {
        if (readyToVerify) {
            logger.info("CM Bulk ImportToLive Verifier Running");
            long startTime = System.nanoTime();
            CmImportToLiveThreadExecutor CmbImToLiveThread = new CmImportToLiveThreadExecutor(this, getHttpRestServiceClient(), fileWriter, cmImportToLiveVerificationNodes);
            CmbImToLiveThread.execute();
            do {
                this.sleep(1L);
                if (CmbImToLiveThread.isCompleted() || CmbImToLiveThread.isErrorOcurred() || CmbImToLiveThread.isTimedOut()) {
                    break;
                }
            } while ((System.nanoTime() - startTime < 120L * (long) Constants.TEN_EXP_9));

            if (CmbImToLiveThread.isErrorOcurred()) {
                logger.warn("Error occured in CmbImportToLive");
            } else if (CmbImToLiveThread.isTimedOut()) {
                logger.warn("CmbImportToLive Timed out.");
            } else if (CmbImToLiveThread.isCompleted()) {
                logger.info("CmbImportToLive Thread: {} completed!", CmbImToLiveThread.getID());
            }
        } else {
            logger.warn("Nothing to verify, the list of CmImportToLive nodes is empty.");
        }
    }



    /**
     * Delete Import Jobs.
     * @param deleteQuery
     */
    private  void deleteJob(final String deleteQuery) throws EnmException, HaTimeoutException {
        final String deleteUri = String.format("/bulk-configuration/v1/import-jobs/jobs?%s", deleteQuery);
        final HttpResponse response;
        try {
            response = getHttpRestServiceClient()
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST)
                    .contentType(APPLICATION_JSON)
                    .delete(deleteUri);
        } catch (final IllegalStateException e) {
            logger.warn(CM_LIVE_TIMEOUT_ERROR, e);
            throw new HaTimeoutException(CM_LIVE_TIMEOUT_ERROR + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
        logger.debug("Delete Response: {}", response.getBody());
    }

    /**
     * Clean all test Jobs.
     * @throws EnmException
     */
    private void cleanAllJobs() throws EnmException, HaTimeoutException {
        final String getUri = String.format("/bulk-configuration/v1/import-jobs/jobs");
        final Gson gson = new GsonBuilder().create();
        String queryString = "";
        List<String> jobsToDelete = new ArrayList<>();
        final HttpResponse response;
        try {
            response = getHttpRestServiceClient()
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST)
                    .contentType(APPLICATION_JSON)
                    .queryParam("search", JOB_PREFIX)
                    .queryParam("limit", "100")
                    .get(getUri);
        } catch (final IllegalStateException e) {
            logger.warn(CM_LIVE_TIMEOUT_ERROR, e);
            throw new HaTimeoutException(CM_LIVE_TIMEOUT_ERROR + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
        if (response.getBody() != null) {
            final JsonObject jobObject = gson.fromJson(((JSONObject) JSONValue.parse(response.getBody())).toJSONString(), JsonObject.class);
            final JsonArray jobArray = jobObject.getAsJsonArray("jobs");
            for (final JsonElement job : jobArray) {
                final JsonObject jobInfo = job.getAsJsonObject();
                final JsonElement jobId = jobInfo.get("id");
                queryString += "jobId=" + jobId.toString() + "&";
                jobsToDelete.add(jobId.toString());
            }
            queryString = queryString.substring(0, queryString.lastIndexOf("&"));
            logger.info("Jobs Ids to delete: {}", jobsToDelete);
            deleteJob(queryString);
        }
    }
}
