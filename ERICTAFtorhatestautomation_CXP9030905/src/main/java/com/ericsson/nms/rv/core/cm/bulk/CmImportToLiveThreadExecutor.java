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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static com.ericsson.cifwk.taf.tools.http.constants.ContentType.APPLICATION_JSON;
import static com.ericsson.nms.rv.core.HAPropertiesReader.ignoreDTMap;
import static com.ericsson.nms.rv.core.cm.ConfigStatesResults.COMPLETED;
import static com.ericsson.nms.rv.core.cm.ConfigStatesResults.EXECUTED;
import static com.ericsson.nms.rv.core.cm.ConfigStatesResults.FAILED;
import static com.ericsson.nms.rv.core.cm.ConfigStatesResults.VALIDATED;
import static com.ericsson.nms.rv.core.cm.bulk.CmImportToLive.cmbImpToLiveIgnoreDTMap;

public class CmImportToLiveThreadExecutor implements Runnable {
    private static final Logger logger = LogManager.getLogger(CmImportToLiveThreadExecutor.class);

    private static final long randomNumber = new Random(System.currentTimeMillis()).nextLong();
    private static final String JOB_PREFIX = "HA_Test_Import_to_Live_";
    private static final String IMPORT_TO_LIVE_CONFIG = JOB_PREFIX + (randomNumber < 0 ? -randomNumber : randomNumber);
    private static final String CM_LIVE_ERROR = "CM Import to Live error.";
    private static final String CM_LIVE_TIMEOUT_ERROR = "CM Import to Live Timeout error.";
    private String FAILED_TO_EXECUTE_COMMAND = "Failed to execute command";
    private String jobId;
    private String fileWriter;

    private boolean completed;
    private boolean errorOccurred;
    private boolean timedOut;
    private Thread worker;
    private EnmApplication application;
    private HttpRestServiceClient httpRestServiceClient;
    private List<String> cmImportToLiveVerificationNodes;
    private long threadId;

    public boolean isCompleted() {
        return completed;
    }

    public boolean isErrorOcurred() {
        return errorOccurred;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public long getID() {
        return threadId;
    }

    public void execute() {
        worker = new Thread(this);
        worker.start();
        threadId = worker.getId();
    }

    public CmImportToLiveThreadExecutor(final EnmApplication application, final HttpRestServiceClient httpRestServiceClient, final String fileWriter, final List<String> cmImportToLiveVerificationNodes) {
        this.application = application;
        this.httpRestServiceClient = httpRestServiceClient;
        this.fileWriter = fileWriter;
        this.cmImportToLiveVerificationNodes = cmImportToLiveVerificationNodes;
    }

    @Override
    public void run() {
        jobId = StringUtils.EMPTY;
        String status = StringUtils.EMPTY;
        try {
            final String fileUri = createJob();
            if (StringUtils.isNotEmpty(fileUri)) {
                importFile(fileUri);
                executeJob(jobId, "validate");
                final long timeOut = HAPropertiesReader.getCmbeTimeToWaitJobComplete();
                final long validateStartTime = System.nanoTime();
                do {
                    status = getJobStatus();
                    logger.debug("Thread id :{} Validation Job--{}  Status: {}",threadId, jobId, status);
                    if (StringUtils.equals(status, VALIDATED)) {
                        break;
                    }
                    application.sleep(5L);
                } while (!EnmApplication.getSignal() && System.nanoTime() - validateStartTime < timeOut * Constants.TEN_EXP_9 &&
                        !(StringUtils.equals(status, VALIDATED) || StringUtils.equals(status, FAILED)));
                if (StringUtils.isEmpty(status)) {
                    throw new EnmException("jobId=" + jobId + ", fileUri=" + fileUri + ", Validation status is EMPTY", EnmErrorType.APPLICATION);
                } else if (!StringUtils.equals(status, VALIDATED)) {
                    throw new EnmException("jobId=" + jobId + ", fileUri=" + fileUri + ", Validation status is FAILED", EnmErrorType.APPLICATION);
                }
                executeJob(jobId, "execute");
                logger.info("Thread id :{} CM Bulk ImportToLive jobId={} is running",threadId, jobId);
                final long executeStartTime = System.nanoTime();
                do {
                    status = getJobStatus();
                    logger.debug("Thread id :{} Execute Job--{} Status {}",threadId, jobId, status);
                    if (StringUtils.equals(status, EXECUTED)) {
                        break;
                    }
                    application.sleep(5L);
                } while (!EnmApplication.getSignal() && System.nanoTime() - executeStartTime < timeOut * Constants.TEN_EXP_9 &&
                        !(StringUtils.equals(status, FAILED) || StringUtils.equals(status, COMPLETED) || StringUtils.equals(status, EXECUTED)));
                if (StringUtils.isNotEmpty(status)) {
                    if (StringUtils.equals(status, FAILED)) {
                        throw new EnmException("jobId=" + jobId + ", fileUri=" + fileUri + ", status=" + status, EnmErrorType.APPLICATION);
                    } else if (StringUtils.equals(status, COMPLETED) || StringUtils.equals(status, EXECUTED)) {
                        completed = true;
                        application.stopAppDownTime();
                        application.stopAppTimeoutDownTime();
                        application.setFirstTimeFail(true);
                        logger.info("Thread id {} Job: {} executed successfully!!",threadId, jobId);
                    }
                } else {
                    throw new EnmException("jobId=" + jobId + ", fileUri=" + fileUri + ", status is EMPTY", EnmErrorType.APPLICATION);
                }
                logger.warn("Thread id {} jobId={}, jobStatus={}", threadId ,jobId, status);
            } else {
                logger.warn("Thread id {} jobId={}, fileUri is EMPTY, status={}", threadId, jobId, status);
                throw new EnmException("jobId=" + jobId + ", fileUri is EMPTY", EnmErrorType.APPLICATION);
            }

        } catch (final IllegalStateException | HaTimeoutException e) {
            logger.warn("Thread id : {} TimedOut Occurred in CMBIL {}",threadId,e.getStackTrace());
            timedOut = true;
            application.stopAppDownTime();
            application.setFirstTimeFail(true);
            application.startAppTimeoutDowntime();
        } catch (final Exception e) {
            logger.warn("Thread id : {} Error Ocurred in CMBIL {}",threadId,e.getStackTrace());
            errorOccurred = true;
            application.stopAppTimeoutDownTime();
            if (application.isFirstTimeFail()) {
                application.setFirstTimeFail(false);
            } else {
                application.startAppDownTime();
            }
        } finally {
            // No DT should be started here as it is cleanup task.
            if (!(jobId.isEmpty())) {
                logger.info("Job id to be deleted is {}", jobId);
                try {
                    deleteJob("jobId=" + jobId);
                    completed = true;
                } catch (final IllegalStateException e) {
                    logger.warn("Thread id : {} TimedOut Ocurred in deleteJob CMBIL {}", threadId, e.getMessage());
                    timedOut = true;
                } catch (final Exception e) {
                    logger.warn("Thread id : {} Error Ocurred in deleteJob CMBIL {}", threadId, e.getMessage());
                    errorOccurred = true;
                }
                jobId = StringUtils.EMPTY;
            }
            CommonUtils.sleep(1);
            Thread.currentThread().stop();
        }
    }


    /**
     * Execute import to Live Jobs.
     *
     * @param jobId
     * @param jobType
     * @throws EnmException
     */
    private void executeJob(final String jobId, final String jobType) throws EnmException, IllegalStateException {
        final String invokeUri = String.format("/bulk-configuration/v1/import-jobs/jobs/%s/invocations", jobId);
        HttpResponse response;
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        try {
            response = httpRestServiceClient
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST)
                    .contentType(APPLICATION_JSON)
                    .body("{\"invocationFlow\":\"" + jobType + "\"}")
                    .post(invokeUri);
        } catch (IllegalStateException var14) {
            logger.warn("Thread id : {} CM Bulk ImportToLive Timeout failed to execute .........createJobUri{} ",threadId, var14.getMessage());
            application.setFirstTimeFail(true);
            application.stopAppDownTime();
            application.startAppTimeoutDowntime();
            response = httpRestServiceClient
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST_100)
                    .contentType(APPLICATION_JSON)
                    .body("{\"invocationFlow\":\"" + jobType + "\"}")
                    .post(invokeUri);
            if (response != null ) {
                long respTime = response.getResponseTimeMillis() + 20000L;
                if (checkResponse(response) && respTime < 120000L) {
                    logger.info("Thread id : {} CM Bulk ImportToLive Delayed Response time: {} .....createJobUri.", threadId, respTime);
                    Date endDate = new Date(startTime + respTime);
                    cmbImpToLiveIgnoreDTMap.put(startDate, endDate);
                    logger.info("Thread id : {} CM Bulk ImportToLive IgnoreDTMap : .......createJobUri.{} ", threadId, cmbImpToLiveIgnoreDTMap);
                    ignoreDTMap.put(HAPropertiesReader.CMBULKIMPORTTOLIVE, cmbImpToLiveIgnoreDTMap);
                }
            }
        } catch (final Exception var15) {
            logger.warn("Thread id : {}CM Bulk ImportToLive failed: ........createJobUri.{} ", threadId, var15.getMessage());
            var15.printStackTrace();
            throw new EnmException("CM Bulk ImportToLive failed ..createJobUri. Message: " + var15.getMessage());
        }
        if (response != null ) {
            logger.info("Thread id : {} JobId--{}, responseCode--{}", threadId, jobId, response.getResponseCode());
            application.analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
            logger.info("Thread id : {} Executing -- JobId: {}, JobType: {}.", threadId, jobId, jobType);
        } else {
            logger.warn("Execute job response is null.");
            throw new EnmException("CMBIL Execute job HttpResponse is NULL!");
        }
    }


    /**
     * Queries managed objects.
     *
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private String createJob() throws EnmException {
        HttpResponse response;

        final JSONObject params = new JSONObject();
        params.put("name", IMPORT_TO_LIVE_CONFIG);

        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{params.toString()});
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        try {
            response = httpRestServiceClient.sendPostRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, bodies, null, "/bulk-configuration/v1/import-jobs/jobs");
        } catch (IllegalStateException var14) {
            logger.warn("Thread id : {} CMBIL Timeout failed to execute .........createJobUri{} ", threadId, var14.getMessage());
            application.setFirstTimeFail(true);
            application.stopAppDownTime();
            application.startAppTimeoutDowntime();
            params.clear();
            long random = new Random(System.currentTimeMillis()).nextLong();
            params.put("name", JOB_PREFIX + (random < 0 ? -random : random));
            response = httpRestServiceClient.sendPostRequest(EnmApplication.TIMEOUT_REST_100, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, bodies, null, "/bulk-configuration/v1/import-jobs/jobs");
            if (response != null) {
                long respTime = response.getResponseTimeMillis() + 20000L;
                if (checkResponse(response) && respTime < 120000L) {
                    logger.info("Thread id : {} CM Bulk ImportToLive Delayed Response time: {} .....createJobUri.", threadId, respTime);
                    Date endDate = new Date(startTime + respTime);
                    cmbImpToLiveIgnoreDTMap.put(startDate, endDate);
                    logger.info("Thread id : {} cmbImpToLiveIgnoreDTMap : .......createJobUri.{} ", threadId, cmbImpToLiveIgnoreDTMap);
                    ignoreDTMap.put(HAPropertiesReader.CMBULKIMPORTTOLIVE, cmbImpToLiveIgnoreDTMap);
                    return String.format("/bulk-configuration/v1/import-jobs/jobs/%s/files", jobId);
                }
            }
        } catch (final Exception var15) {
            logger.warn("Thread id : {} CM Bulk ImportToLive failed: ........createJobUri.{} ", threadId, var15.getMessage());
            var15.printStackTrace();
            throw new EnmException("CM Bulk ImportToLive failed ..createJobUri. Message: " + var15.getMessage());
        }

        if (response != null) {
            logger.debug("Thread id : {} CMBIL response code {}", threadId, response.getResponseCode());
            application.analyzeResponseWithoutStopAppDownTime(FAILED_TO_EXECUTE_COMMAND, response);
            jobId = ((JSONObject) JSONValue.parse(response.getBody())).getAsString("id");
            return String.format("/bulk-configuration/v1/import-jobs/jobs/%s/files", jobId);
        } else {
            logger.warn("Thread id : {} For ActionType (POST) and action ({}) HttpResponse is NULL!", threadId, CM_LIVE_ERROR);
            return StringUtils.EMPTY;
        }
    }

    /**
     * Queries managed objects.
     *
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private void importFile(final String fileUri) throws EnmException, IllegalStateException {
        HttpResponse response;
        final InputStream stream;
        final String filename = "cmbil_import_file.xml";

        try {
            logger.info("Thread id {} Node importFile: {}", threadId, cmImportToLiveVerificationNodes.get(0));
            stream = new ByteArrayInputStream(fileWriter.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            e.printStackTrace();
            throw new EnmException("Failed to update import file contents!", EnmErrorType.APPLICATION);
        }
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        try {
            response = httpRestServiceClient
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST)
                    .contentType(ContentType.MULTIPART_FORM_DATA)
                    .body("file", stream)
                    .body("filename", filename)
                    .post(fileUri);
        } catch (IllegalStateException var14) {
            logger.warn("Thread id {} CM Bulk ImportToLive Timeout failed to execute .........createJobUri{} ", threadId, var14.getMessage());
            application.setFirstTimeFail(true);
            application.stopAppDownTime();
            application.startAppTimeoutDowntime();
            response = httpRestServiceClient
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST_100)
                    .contentType(ContentType.MULTIPART_FORM_DATA)
                    .body("file", stream)
                    .body("filename", filename)
                    .post(fileUri);
            if (response != null) {
                long respTime = response.getResponseTimeMillis() + 20000L;
                if (checkResponse(response) && respTime < 120000L) {
                    logger.info("Thread id {} CM Bulk ImportToLive Delayed Response time: {} .....createJobUri. ", threadId, respTime);
                    Date endDate = new Date(startTime + respTime);
                    cmbImpToLiveIgnoreDTMap.put(startDate, endDate);
                    logger.info("Thread id {} CM Bulk ImportToLive IgnoreDTMap : .......createJobUri.{} ", threadId, cmbImpToLiveIgnoreDTMap);
                    ignoreDTMap.put(HAPropertiesReader.CMBULKIMPORTTOLIVE, cmbImpToLiveIgnoreDTMap);
                }
            }
        } catch (Exception var15) {
            logger.warn("Thread id {} CM Bulk ImportToLive failed: ........createJobUri.{} ", threadId, var15.getMessage());
            var15.printStackTrace();
            throw new EnmException("CM Bulk ImportToLive failed ..createJobUri. Message: " + var15.getMessage());
        }

        if (response != null) {
            logger.debug("Thread id {} CMBIL response code {}",threadId, response.getResponseCode());
            application.analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
        } else {
            logger.warn("Thread id {} For ActionType (POST) and action ({}) HttpResponse is NULL!", threadId, CM_LIVE_ERROR);
            throw new EnmException("CMBIL importFile HttpResponse is NULL!");
        }
    }

    private String getJobStatus() throws EnmException, HaTimeoutException {
        try {
            return getJobStatus(jobId);
        } catch (final IllegalStateException e) {
            logger.warn(CM_LIVE_TIMEOUT_ERROR, e);
            throw new HaTimeoutException(CM_LIVE_TIMEOUT_ERROR + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
    }

    /**
     * Delete Import Jobs.
     * @param deleteQuery
     */
    private void deleteJob(final String deleteQuery) throws EnmException, IllegalStateException {
        final String deleteUri = String.format("/bulk-configuration/v1/import-jobs/jobs?%s", deleteQuery);
        HttpResponse response;
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);

        try {
            response = httpRestServiceClient
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST)
                    .contentType(APPLICATION_JSON)
                    .delete(deleteUri);
        } catch (IllegalStateException var14) {
            logger.warn("Thread id {} CM Bulk ImportToLive Timeout failed to execute .........createJobUri{} ",threadId, var14.getMessage());
            application.setFirstTimeFail(true);
            application.stopAppDownTime();
            application.startAppTimeoutDowntime();
            response = httpRestServiceClient
                    .getHttpTool()
                    .request()
                    .timeout(EnmApplication.TIMEOUT_REST_100)
                    .contentType(APPLICATION_JSON)
                    .delete(deleteUri);
            if (response != null) {
                long respTime = response.getResponseTimeMillis() + 20000L;
                if (checkResponse(response) && respTime < 120000L) {
                    logger.info("Thread id {} CMBIL Delayed Response time: {} .....createJobUri.", threadId, respTime);
                    Date endDate = new Date(startTime + respTime);
                    cmbImpToLiveIgnoreDTMap.put(startDate, endDate);
                    logger.info("Thread id {} CM Bulk ImportToLive IgnoreDTMap : .......createJobUri.{} ", threadId, cmbImpToLiveIgnoreDTMap);
                    ignoreDTMap.put(HAPropertiesReader.CMBULKIMPORTTOLIVE, cmbImpToLiveIgnoreDTMap);
                }
            }
        } catch (Exception var15) {
            logger.warn("Thread id {} CM Bulk ImportToLive failed: ........createJobUri.{} ", threadId, var15.getMessage());
            var15.printStackTrace();
            throw new EnmException("CM Bulk ImportToLive failed ..createJobUri. Message: " + var15.getMessage());
        }
        if (response != null ) {
            application.analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
            logger.debug("Thread id {} Delete Response: {}", threadId, response.getBody());
        } else {
            logger.warn("deleteJob job response is null.");
            throw new EnmException("CMBIL delete job HttpResponse is NULL!");
        }
    }

    private String getJobStatus(final String jobId) throws EnmException, IllegalStateException {

        final String uri = String.format("/bulk-configuration/v1/import-jobs/jobs/%s", jobId);
        HttpResponse response;
        try {
            response = application.getHttpRestServiceClient().sendGetRequest(
                    EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);

        } catch (IllegalStateException var14) {
            logger.warn("Thread id {} CM Bulk ImportToLive Timeout failed to execute .........createJobUri{} ",threadId, var14.getMessage());
            response = application.getHttpRestServiceClient().sendGetRequest(
                    EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
        } catch (final Exception var15) {
            logger.warn("Thread id {} CM Bulk ImportToLive failed: ........createJobUri.{} ", threadId, var15.getMessage());
            return StringUtils.EMPTY;
        }

        if (response != null) {
            application.analyzeResponse(FAILED_TO_EXECUTE_COMMAND, response);
            return ((JSONObject) JSONValue.parse(response.getBody())).getAsString("status");
        } else {
            logger.warn("Thread id {} For ActionType (POST) and action cmImportToLive HttpResponse is NULL! {} failed", threadId, application.getClass().getSimpleName());
            return StringUtils.EMPTY;
        }
    }

    private boolean checkResponse(HttpResponse response) throws IllegalStateException{
        if (EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            application.setFirstTimeFail(true);
            application.stopAppTimeoutDownTime();
            application.stopAppDownTime();
            return true;
        } else if (response != null && response.getResponseCode().getCode() == 504) {
            throw new IllegalStateException(response.getResponseCode().toString());
        } else {
            return false;
        }
    }
}
