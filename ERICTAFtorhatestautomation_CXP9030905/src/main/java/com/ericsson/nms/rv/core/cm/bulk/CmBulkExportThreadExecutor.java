package com.ericsson.nms.rv.core.cm.bulk;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.ericsson.nms.rv.core.cm.ConfigStatesResults.COMPLETED;
import static com.ericsson.nms.rv.core.cm.ConfigStatesResults.FAILED;

public class CmBulkExportThreadExecutor implements Runnable {

    private static final Logger logger = LogManager.getLogger(CmBulkExportThreadExecutor.class);
    private static final String CMBE_ERROR = "CMBE error";
    private static final String CMBE_TIMEOUT_ERROR = "CMBE Timeout error";
    private static final String FAILED_TO_EXECUTE_COMMAND = "Failed to execute command";
    private List<String> cmBulkExportVerificationNodes;
    private String jobId;
    public static final String CMBE_BUSY = "8027";
    private Thread worker;
    private EnmApplication application;
    private HttpRestServiceClient httpRestServiceClient;
    private boolean completed;
    private boolean ErrorOccurance;
    private boolean timedOut;
    private boolean busy;
    private long threadId;

    public boolean isBusy() {
        return busy;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public boolean isErrorOcurred() {
        return this.ErrorOccurance;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public CmBulkExportThreadExecutor(EnmApplication application, HttpRestServiceClient httpRestServiceClient, List<String> cmBulkExportVerificationNodes) {
        this.application = application;
        this.httpRestServiceClient = httpRestServiceClient;
        this.cmBulkExportVerificationNodes = cmBulkExportVerificationNodes;
    }

    public long getID() {
        return this.worker.getId();
    }

    public void execute() {
        this.worker = new Thread(this);
        this.worker.start();
        threadId = this.worker.getId();
    }


    @Override
    public void run() {
        try {
            String status;
            final String jobResponse = createJob();
            if (jobResponse == CMBE_BUSY) {
                logger.warn("Thread id : {} CMBE server is busy, response code : {}", threadId, CMBE_BUSY);
                this.busy=true;
                return;
            }
            logger.info("Thread id : {} CM Bulk Export jobId = {}, created", threadId, jobId);
            if(StringUtils.isEmpty(jobResponse)){
                logger.warn("CMBE failed to create job, createJob response is EMPTY");
                throw new EnmException("CM Bulk Export failed to create job, createJob response is EMPTY", EnmErrorType.APPLICATION);
            }
            final long timeOut = HAPropertiesReader.getCmbeTimeToWaitJobComplete();
            final long startTime = System.nanoTime();

            do {
                status = getJobStatus();
                logger.info("Thread id : {} CMBE Verify Job : {}, status : {}", threadId, jobId, status);
                if (StringUtils.equals(status, COMPLETED)) {
                    break;
                }
                application.sleep(5L);
            } while (!EnmApplication.getSignal() && System.nanoTime() - startTime < timeOut * Constants.TEN_EXP_9 &&
                    !(StringUtils.equals(status, FAILED) || StringUtils.equals(status, COMPLETED)));

            if (status == CMBE_BUSY) {
                logger.warn("Thread id : {} CMBE server is busy, Job : {}, Status response code : {}", threadId, jobId, CMBE_BUSY);
                this.busy = true;
                return;
            }

            if (StringUtils.isNotEmpty(status)) {
                if (StringUtils.equals(status, FAILED)) {
                    logger.warn("Thread id : {} CM Bulk Export jobId = {}, FAILED", threadId,jobId);
                    throw new EnmException("CM Bulk Export jobId = " + jobId + ", FAILED", EnmErrorType.APPLICATION);
                } else if (StringUtils.equals(status, COMPLETED)) {
                    this.completed = true;
                    application.stopAppDownTime();
                    application.stopAppTimeoutDownTime();
                    application.setFirstTimeFail(true);
                }
                logger.info("Thread id : {} jobId = {}, jobStatus = {}",threadId, jobId, status);
            } else {
                logger.warn("Thread id : {}  jobId = {}, jobStatus = {}", threadId,jobId, status);
                throw new EnmException("CM Bulk Export jobId = " + jobId + ", jobStatus is EMPTY", EnmErrorType.APPLICATION);
            }
        } catch (EnmException e) {
            this.ErrorOccurance = true;
            this.application.stopAppTimeoutDownTime();
            if (this.application.isFirstTimeFail()) {
                this.application.setFirstTimeFail(false);
            } else {
                this.application.startAppDownTime();
            }
            e.printStackTrace();
        } catch (HaTimeoutException | IllegalStateException e) {
            this.timedOut = true;
            this.application.stopAppDownTime();
            this.application.setFirstTimeFail(true);
            this.application.startAppTimeoutDowntime();
            e.printStackTrace();
        } catch (Exception e) {
            this.ErrorOccurance = true;
            this.application.stopAppTimeoutDownTime();
            if (this.application.isFirstTimeFail()) {
                this.application.setFirstTimeFail(false);
            } else {
                this.application.startAppDownTime();
            }
            e.printStackTrace();
        } finally {
            CommonUtils.sleep(1);
            Thread.currentThread().stop();
        }
    }

    /**
     * Queries managed objects.
     *
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String createJob() throws EnmException, IllegalStateException, HaTimeoutException {
        HttpResponse response;

        final List<JSONObject> nodeSearchScopesParams = new ArrayList<>();
        final String internalErrorCode;

        for (final String nodeId : cmBulkExportVerificationNodes) {
            final JSONObject nodeSearchScopeParams = new JSONObject();
            nodeSearchScopeParams.put("scopeType", "NODE_NAME");
            nodeSearchScopeParams.put("matchCondition", "EQUALS");
            nodeSearchScopeParams.put("value", nodeId);
            nodeSearchScopesParams.add(nodeSearchScopeParams);
        }

        final JSONObject nodeSearchCriteriaParams = new JSONObject();

        nodeSearchCriteriaParams.put("nodeSearchScopes", nodeSearchScopesParams);

        final JSONObject params = new JSONObject();
        params.put("type", "EXPORT");
        params.put("configName", "LIVE");
        params.put("fileFormat", "3GPP");
        params.put("nodeSearchCriteria", nodeSearchCriteriaParams);
        final List<String[]> bodies = new ArrayList<>();
        long  startTime = System.currentTimeMillis();
        try {
            final Random random = new Random(System.currentTimeMillis());
            final Long uniqueNo = random.nextLong();
            final String jobName = "HA_cm_bulk_export_job_" + (uniqueNo < 0L ? -uniqueNo : uniqueNo);
            params.put("jobName", jobName);
            bodies.add(new String[]{params.toString()});
            logger.info("Thread id : {} CmBulk Export jobName : {}", threadId, jobName);
            startTime = System.currentTimeMillis();
            response = httpRestServiceClient.sendPostRequest(120, "application/json", new String[]{"ContentType", "application/json"}, bodies, null, "/bulk/export/jobs");
        } catch (IllegalStateException var14) {
            final long timeOutTime = System.currentTimeMillis() - startTime;
            logger.warn("Thread id : {} CMBE Timeout in {}ms failed to execute createJob : {}", threadId, timeOutTime ,var14.getMessage());
            throw new HaTimeoutException(CMBE_TIMEOUT_ERROR + " : " + var14.getMessage(), EnmErrorType.APPLICATION);
        }

        if (!( JSONValue.parse(response.getBody()) instanceof JSONObject )) {
            logger.warn("Thread id : {} CMBE createJob response is not a JSONObject : {}", threadId, response.getBody());
            throw new EnmException("CMBE failed to create job", EnmErrorType.APPLICATION);
        }

        try {
            internalErrorCode = ((JSONObject) JSONValue.parse(response.getBody())).getAsString("internalErrorCode");
        } catch (final Exception e) {
            if (response != null) {
                logger.warn("Thread id : {} Response : {}", threadId, response.getBody());
            }
            throw new EnmException("Failed to read response : " + e.getMessage(), EnmErrorType.APPLICATION);
        }

        if (internalErrorCode != null && Integer.parseInt(internalErrorCode) == 8027) {
            logger.info("Thread id : {} internalErrorCode : {}", threadId, internalErrorCode);
            String message = ((JSONObject) JSONValue.parse(response.getBody())).getAsString("userMessage");
            logger.warn("Thread id : {} Message : {}", threadId, message);
            application.stopAppDownTime();
            application.stopAppTimeoutDownTime();
            application.setFirstTimeFail(true);
            return CMBE_BUSY;
        }

        if (response != null) {
            final JSONObject params1 = (JSONObject) JSONValue.parse(response.getBody());
            logger.debug("Thread id : {} CMBE createJob response code {} : {}",threadId, response.getResponseCode(), response.getResponseCode().getCode());
            application.analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
            jobId = params1.getAsString("id");
            return response.getBody();
        } else {
            logger.warn("Thread id : {} For ActionType (POST) and action ({}) HttpResponse is NULL!", threadId, CMBE_ERROR);
            return StringUtils.EMPTY;
        }
    }

    /**
     * Queries managed objects.
     *
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String getJobStatus() throws EnmException {
        return getJobStatus(application, "export", jobId);
    }


    private String getJobStatus(final EnmApplication application, final String command, final String jobId) throws EnmException {
        final String internalErrorCode;
        final String uri = String.format("/bulk/%s/jobs/%s", command, jobId);
        HttpResponse response;
        long startTime = System.currentTimeMillis();
        try {
            response = httpRestServiceClient.sendGetRequest(20, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
        } catch (IllegalStateException var14) {
            final long timeOutTime = System.currentTimeMillis() - startTime;
            logger.warn("Thread id : {} CMBE Timeout in {}ms failed to execute getJobStatus : {}", threadId, timeOutTime ,var14.getMessage());
            return StringUtils.EMPTY;
        }

        if (!( JSONValue.parse(response.getBody()) instanceof JSONObject )) {
            logger.warn("Thread id : {} CMBE getJobStatus response is not a JSONObject : {} ",threadId,response.getBody());
            throw new EnmException("CMBE failed to get job status", EnmErrorType.APPLICATION);
        }

        try {
            internalErrorCode = ((JSONObject) JSONValue.parse(response.getBody())).getAsString("internalErrorCode");
        } catch (final Exception e) {
            if (response != null) {
                logger.warn("Thread id : {} Response : {}", threadId, response.getBody());
            }
            throw new EnmException("Failed to read response : " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        if (internalErrorCode != null && Integer.parseInt(internalErrorCode) == 8027) {
            logger.info("Thread id : {} Job status response code : {}", threadId, response.getResponseCode().getCode());
            logger.info("Thread id : {} internalErrorCode : {}", threadId, internalErrorCode);
            this.application.setFirstTimeFail(true);
            this.application.stopAppDownTime();
            this.application.stopAppTimeoutDownTime();
            return CMBE_BUSY;
        }

        if (response != null) {
            this.application.analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
            return ((JSONObject) JSONValue.parse(response.getBody())).getAsString("status");
        } else {
            logger.warn("Thread id : {} For ActionType (POST) and action ({}) HttpResponse is NULL! {} failed",threadId, command, application.getClass().getSimpleName());
            return StringUtils.EMPTY;
        }
    }
}
