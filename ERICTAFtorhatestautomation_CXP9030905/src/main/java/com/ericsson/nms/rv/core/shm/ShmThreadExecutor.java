package com.ericsson.nms.rv.core.shm;


import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.LoadGenerator;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.ericsson.nms.rv.core.HAPropertiesReader.ignoreDTMap;
import static com.ericsson.nms.rv.core.shm.SoftwareHardwareManagement.shmIgnoreDTMap;


public class ShmThreadExecutor implements Runnable {

    private static final Logger logger = LogManager.getLogger(ShmThreadExecutor.class);
    private Thread worker;
    private HttpRestServiceClient httpRestClient;
    private EnmApplication application;
    private boolean completed;
    private boolean timedOut;
    private boolean ErrorOccurance;
    private long threadId;
    private  List<Node> nodes;

    public boolean isCompleted() {
        return completed;
    }


    public boolean isErrorOcurred() {
        return ErrorOccurance;
    }


    public boolean isTimedOut() {
        return timedOut;
    }


    public static Logger getLogger() {
        return logger;
    }

    ShmThreadExecutor(HttpRestServiceClient httpRestClient, List<Node> nodes, EnmApplication application) {
        this.httpRestClient = httpRestClient;
        this.application = application;
        this.nodes = nodes;
    }

    public void execute() {
        worker = new Thread(this);
        threadId = worker.getId();
        worker.start();
    }

    public long getID() {
        return worker.getId();
    }

    @Override
    public void run() {
        try {
            final String jobName = createBackupJob();
            if (StringUtils.isNotEmpty(jobName)) {
                logger.info("ThreadId: {} SHM Backup Job command executed successfully, jobName : {}", threadId, jobName);
                final SHMJob shmJob = viewJobs(jobName);

                if (shmJob == null) {
                    logger.info("ThreadId: {} for jobName : {}, Unable to get job details.", threadId, jobName);
                } else {
                    logger.info("ThreadId: {} SHM Backup job's ({}) details successfully received.", threadId, jobName);
                    final List<SHMJob> shmJobList = new ArrayList<>();
                    shmJobList.add(shmJob);
                    if (deleteJobs(shmJobList)) {
                        logger.info("ThreadId: {} SHM Backup job ({}) deleted successfully.", threadId, jobName);
                    } else {
                        logger.warn("ThreadId: {} SHM Backup job ({}) is not deleted.", threadId, jobName);
                    }
                }
            } else {
                logger.info("ThreadId: {} SHM failed in creating Backup Job. jobName : ({})", threadId, jobName);
            }
            completed = true;
        } catch (final IllegalStateException h) {
            timedOut = true;
            logger.warn("ThreadId: {} .. SHM failed : {}", threadId, h.getMessage());
        } catch (final Exception e) {
            ErrorOccurance = true;
            logger.warn("ThreadId: {} .. SHM failed : {}", threadId, e.getMessage());
            application.stopAppTimeoutDownTime();
            if (application.isFirstTimeFail()) {
                application.setFirstTimeFail(false);
            } else {
                application.startAppDownTime();
            }
        } finally {
            Thread.currentThread().stop();
        }

    }

    private SHMJob viewJobs(final String jobName) throws EnmException {
        SHMJob shmJob;
        Object parse = null;
        final String HA_BACKUP_JOB_ADMINISTRATOR = "HA_BackupJob_administrator_";
        try (final InputStream inputStream = ShmThreadExecutor.class.getResourceAsStream("/json/shmViewJobs.json")) {
            parse = JSONValue.parse(inputStream);
        } catch (final Exception e) {
            logger.error("ThreadId: {} Failed to read jsonFile, root cause: ",threadId, e);
        }
        if (parse instanceof JSONObject) {
            final JSONObject jsonObject = (JSONObject) parse;
            final Object filterDetails = jsonObject.get("filterDetails");
            if (filterDetails instanceof JSONArray) {
                final JSONArray details = (JSONArray) filterDetails;
                final Object o = details.get(0);
                if (o instanceof JSONObject) {
                    final JSONObject object = (JSONObject) o;
                    if (jobName != null && !jobName.isEmpty()) {
                        object.put("filterText", jobName);
                    } else {
                        object.put("filterText", HA_BACKUP_JOB_ADMINISTRATOR);
                        logger.info("ThreadId: {} object2 : {}", threadId, object);
                    }
                }
            }
            logger.info("ThreadId: {} Creating SHM Backup Job {}...", threadId, jobName);
            final long createJobStartTime = System.nanoTime();
            do {
                shmJob = parseJobStatusJson(jsonObject);
                application.sleep(1L);
            } while (!EnmApplication.getSignal() && shmJob == null && (System.nanoTime() - createJobStartTime < 120L *  Constants.TEN_EXP_9));//timeout added in RTD-20152.

            if(shmJob != null) {
                logger.info("ThreadId: {} SHM Backup Job ({}) created successfully", threadId, shmJob);
            } else {
                logger.warn("ThreadId: {} SHM Backup Job ({}) is not created yet", threadId, jobName);
                return null;
            }

            logger.info("ThreadId: {} Waiting for SHM Backup Job {} to complete...", threadId, jobName);
            final long completeJobStartTime = System.nanoTime();
            do {
                shmJob = parseJobStatusJson(jsonObject);
                application.sleep(1L);
            } while (!EnmApplication.getSignal() && (System.nanoTime() - completeJobStartTime < 420L * Constants.TEN_EXP_9) && (shmJob == null || (!shmJob.getJobStatus().contains("COMPLETED") && !shmJob.getJobStatus().contains("FAILED"))));//timeout added in RTD-20152.

            if(shmJob ==  null) {
                logger.warn("ThreadId: {} Unable to get details of SHM Backup Job ({})", threadId, jobName);
            } else if(shmJob.getJobStatus().contains("COMPLETED") || shmJob.getJobStatus().contains("FAILED")) {
                logger.info("ThreadId: {} SHM Backup Job ({}) Completed successfully", threadId, shmJob);
            } else if(shmJob.getJobStatus().contains("RUNNING")) {
                logger.warn("ThreadId: {} SHM Backup Job ({}) is still running", threadId, shmJob);
            } else if(shmJob.getJobStatus().contains("CREATED")) {
                logger.warn("ThreadId: {} SHM Backup Job ({}) is not running yet", threadId, shmJob);
            }
            return shmJob;
        }
        return null;
    }

    private String createBackupJob() throws EnmException {
        Object parse = null;
        final String FAILED_TO_CREATE_BACKUP_JOB = "Failed to create SHM Backup Job";
        final String SHM_REST_URL = "/oss/shm/rest/job";
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        final String backupJson;
        if (LoadGenerator.isErbsNodesAvailable) {
            backupJson = "/json/shmCreateBackupJob.json";
        } else {
            backupJson = "/json/shmCreateBackupJobForRadionode.json";
        }
        try (final InputStream inputStream = ShmThreadExecutor.class.getResourceAsStream(backupJson)) {
            parse = JSONValue.parse(inputStream);
        } catch (final IOException e) {
            logger.error("ThreadId: {} Failed to read jsonFile, root cause: ", threadId, e);
        }
        if (parse instanceof JSONObject) {
            JSONObject jsonObject = prepareBody((JSONObject) parse);
            if (jsonObject != null) {
                final List<String[]> bodies = new ArrayList<>();
                bodies.add(new String[]{jsonObject.toString()});
                HttpResponse response;
                try {
                    response = application.getHttpRestServiceClient().sendPostRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, SHM_REST_URL);
                } catch (IllegalStateException var14) {
                    logger.warn("ThreadId: {} SHM Timeout failed to execute : {} ", threadId, var14.getMessage());
                    application.setFirstTimeFail(true);
                    application.stopAppDownTime();
                    application.startAppTimeoutDowntime();
                    timedOut = true;
                    jsonObject = prepareBody((JSONObject) parse);
                    if (jsonObject == null) {
                        return Constants.EMPTY_STRING;
                    }
                    bodies.clear();
                    bodies.add(new String[]{jsonObject.toString()});
                    response = application.getHttpRestServiceClient().sendPostRequest(100, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, SHM_REST_URL);
                    long respTime = response.getResponseTimeMillis() + 20000L;
                    if (response != null && checkResponse(response) && respTime < 120000L) {
                        logger.info("ThreadId: {} Delayed Response time: {}", threadId, respTime);
                        Date endDate = new Date(startTime + respTime);
                        shmIgnoreDTMap.put(startDate, endDate);
                        logger.info("ThreadId: {} shmIgnoreDTMap : .........{} ", threadId, shmIgnoreDTMap);
                        ignoreDTMap.put(HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT, shmIgnoreDTMap);
                    }
                } catch (Exception var15) {
                    logger.warn("ThreadId: {} SHM create backup job failed : {}", threadId, var15.getMessage());
                    throw new EnmException("SHM failed ... Message: " + var15.getMessage());
                }
                if (response != null) {
                    application.analyzeResponseWithoutStopAppDownTime(FAILED_TO_CREATE_BACKUP_JOB, response);//print response
                    final Object parseBody = JSONValue.parse(response.getBody());
                    logger.info("ThreadId: {} parseBody : {}", threadId, parseBody);
                    if (parseBody instanceof JSONObject) {
                        application.stopAppTimeoutDownTime(response.getResponseTimeMillis());
                        application.stopAppDownTime(response.getResponseTimeMillis());
                        application.setFirstTimeFail(true);
                        return (String) ((JSONObject) parseBody).get("jobName");
                    } else {
                        logger.warn("ThreadId: {}, SHM create job response is not JSON : {}", threadId, parseBody);
                        throw new EnmException("SHM create job failed... responseBody is not JSON : " + parseBody);
                    }
                } else {
                    logger.warn("ThreadId: {} For ActionType (POST) and action ({}) HttpResponse is NULL!",threadId, SHM_REST_URL);
                    throw new EnmException("SHM create job failed... response is NULL");
                }
            }
        }
        return Constants.EMPTY_STRING;
    }

    private JSONObject prepareBody(final JSONObject parse) {
        final JSONObject jsonObject = parse;
        final String HA_BACKUP_JOB_ADMINISTRATOR = "HA_BackupJob_administrator_";
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        long dateTimeStamp = Long.parseLong(sdf.format(new Date()));
        final String jobName = HA_BACKUP_JOB_ADMINISTRATOR + dateTimeStamp;
        jsonObject.put("name", jobName);

        final JSONArray neNames = new JSONArray();
        final List<String> neNamesList = nodes.stream().map(Node::getNetworkElementId).collect(Collectors.toList());
        for (final String neName : neNamesList) {
            final JSONObject object = new JSONObject();
            object.put("name", neName);
            neNames.add(object);
        }
        jsonObject.put("neNames", neNames);


        final JSONArray configurations = (JSONArray) (jsonObject).get("configurations");
        for (final Object config : configurations) {
            if (config instanceof JSONObject && ((JSONObject) config).containsKey("neType")) {
                final JSONArray properties = (JSONArray) ((JSONObject) config).get("properties");
                dateTimeStamp = Long.parseLong(sdf.format(new Date()));
                for (final Object prop : properties) {
                    if (prop instanceof JSONObject && (((String) ((JSONObject) prop).get("key")).contains("CV_NAME") || ((String) ((JSONObject) prop).get("key")).contains("BACKUP_NAME"))) {
                        ((JSONObject) prop).put("value", "Backup_" + dateTimeStamp);
                    }
                }
            }
        }
        return jsonObject;
    }

    private SHMJob parseJobStatusJson(final JSONObject jsonObject) throws EnmException {
        final String FAILED_TO_VIEW_SHM_JOBS = "Failed to view SHM jobs";
        final String VIEW_JOBS_URL = "/oss/shm/rest/job/v2/jobs";
        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});
        HttpResponse response;
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);

        try {
            response = application.getHttpRestServiceClient().sendPostRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, VIEW_JOBS_URL);
        } catch (IllegalStateException var14) {
            logger.warn("ThreadId: {} SHM Timeout failed to execute : {}", threadId, var14.getMessage());
            application.setFirstTimeFail(true);
            application.stopAppDownTime();
            application.startAppTimeoutDowntime();
            timedOut = true;
            response = application.getHttpRestServiceClient().sendPostRequest(100, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, VIEW_JOBS_URL);
            long respTime = response.getResponseTimeMillis() + 20000L;
            if (response != null && checkResponse(response) && respTime < 120000L) {
                logger.info("ThreadId: {} Delayed Response time: {} ...... thread id {}",threadId, respTime, worker.getId());
                Date endDate = new Date(startTime + respTime);
                shmIgnoreDTMap.put(startDate, endDate);
                logger.info("ThreadId: {} shmIgnoreDTMap : {}", threadId, shmIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT, shmIgnoreDTMap);
            }
        } catch (Exception var15) {
            logger.warn("ThreadId: {} SHM parseJobStatusJson failed: {}", threadId, var15.getMessage());
            throw new EnmException("SHM failed to get Job status... Message: " + var15.getMessage());
        }
        if (response != null) {
            application.analyzeResponse(FAILED_TO_VIEW_SHM_JOBS, response);
            return getJobIdsAndStatus(response);
        } else {
            logger.warn("ThreadId: {} For ActionType (POST) and action ({}) HttpResponse is NULL!",threadId, VIEW_JOBS_URL);
            throw new EnmException("SHM failed to get job status... response is NULL");
        }
    }

    private boolean deleteJobs(final List<SHMJob> shmJobList) throws EnmException {
        final List<String> shmJobsIds = new ArrayList<>();
        final String DELETE_JOB_URL = "/oss/shm/rest/job/delete";
        final String FAILED_TO_DELETE_SHM_JOBS = "Failed to delete SHM job : %s";
        final long startTime = System.currentTimeMillis();
        Date startDate = new Date(startTime);
        if (shmJobList.isEmpty()) {
            logger.warn("ThreadId: {} Nothing to delete, job's list is empty.",threadId);
            return false;
        }

        final JSONArray jsonObject = new JSONArray();
        for (final SHMJob shmJob : shmJobList) {
            jsonObject.add(shmJob.getJobId());
            shmJobsIds.add(shmJob.getJobId());
        }

        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});

        HttpResponse response;
        try {
            response = application.getHttpRestServiceClient().sendPostRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, DELETE_JOB_URL);
        } catch (IllegalStateException var14) {
            logger.warn("ThreadId: {} SHM Timeout failed to execute : {}", threadId, var14.getMessage());
            application.setFirstTimeFail(true);
            application.stopAppDownTime();
            application.startAppTimeoutDowntime();
            timedOut = true;
            response = application.getHttpRestServiceClient().sendPostRequest(100, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, DELETE_JOB_URL);
            long respTime = response.getResponseTimeMillis() + 20000L;
            if (response != null && checkResponse(response) && respTime < 120000L) {
                logger.info("ThreadId: {} Delayed Response time: {} ...... thread id {}", threadId, respTime, worker.getId());
                Date endDate = new Date(startTime + respTime);
                shmIgnoreDTMap.put(startDate, endDate);
                logger.info("ThreadId: {} shmIgnoreDTMap : {}", threadId, shmIgnoreDTMap);
                ignoreDTMap.put(HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT, shmIgnoreDTMap);
            }
        } catch (Exception var15) {
            logger.warn("ThreadId: {} SHM delete job failed: {}", threadId, var15.getMessage());
            throw new EnmException("SHM failed to delete job... Message: " + var15.getMessage());
        }
        if (response != null) {
            application.analyzeResponse(String.format(FAILED_TO_DELETE_SHM_JOBS, shmJobsIds), response);
            return response.getBody().contains("success");
        } else {
            logger.warn("ThreadId: {} For ActionType (POST) and action ({}) HttpResponse is NULL!", threadId, DELETE_JOB_URL);
            throw new EnmException("SHM failed to delete job... response is NULL");
        }
    }

    private SHMJob getJobIdsAndStatus(final HttpResponse response) {
        final List<SHMJob> shmList = new ArrayList<>();

        final Object body = JSONValue.parse(response.getBody());
        if (body instanceof JSONObject) {
            final Object result = ((JSONObject) body).get("result");
            if (result instanceof JSONArray) {
                for (final Object o : (JSONArray) result) {
                    if (o instanceof JSONObject) {
                        final JSONObject object = (JSONObject) o;
                        shmList.add(new SHMJob(object.getAsString("jobName"), object.getAsString("jobId"), object.getAsString("status"), object.getAsString("result")));
                    }
                }
            }
        }
        return (shmList.isEmpty()) ? null : shmList.get(0);
    }


    private boolean checkResponse(HttpResponse response) {
        if (EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
            application.stopAppTimeoutDownTime();
            application.stopAppDownTime();
            application.setFirstTimeFail(true);
            return true;
        } else if (response.getResponseCode().getCode() == 504) {
            logger.warn("ThreadId: {} SHM timed out, responseBody : {}", threadId, response.getBody());
            throw new IllegalStateException(response.getResponseCode().toString());
        } else {
            return false;
        }
    }
}
