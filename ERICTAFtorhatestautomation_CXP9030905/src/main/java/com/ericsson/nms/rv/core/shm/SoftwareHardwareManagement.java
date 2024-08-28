package com.ericsson.nms.rv.core.shm;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;


import com.ericsson.nms.rv.core.util.HaTimeoutException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

/**
 * {@code SoftwareHardwareManagement} allows to run Software Hardware Manager queries.
 */
public final class SoftwareHardwareManagement extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(SoftwareHardwareManagement.class);
    private static final String SHM_REST_URL = "/oss/shm/rest/job";
    private static final String VIEW_JOBS_URL = SHM_REST_URL + "/v2/jobs";
    private static final String DELETE_JOB_URL = SHM_REST_URL + "/delete";

    private static final String FAILED_TO_VIEW_SHM_JOBS = "Failed to view SHM jobs";
    private static final String FAILED_TO_DELETE_SHM_JOBS = "Failed to delete SHM job";
    private static final String FAILED_TO_CREATE_BACKUP_JOB = "Failed to create SHM Backup Job";
    private static final String HA_BACKUP_JOB_ADMINISTRATOR = "HA_BackupJob_administrator_";
    private final List<Node> nodes = new ArrayList<>();
    public static NavigableMap<Date, Date> shmIgnoreDTMap = new ConcurrentSkipListMap();

    public SoftwareHardwareManagement(final ShmLoadGenerator shmLoadGenerator, final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
        nodes.addAll(shmLoadGenerator.getShmVerificationNodes());

        logger.info("SoftwareHardwareManagement Using nodes {}", getUsedNodes(nodes));
    }

    @Override
    public void verify() throws EnmException, HaTimeoutException {
        logger.info("SHM Verifier Running");
        ShmThreadExecutor thread = new ShmThreadExecutor(getHttpRestServiceClient(), nodes,this);
        thread.execute();
        long startTime = System.nanoTime();
        logger.info("SHM ThreadId: {} started.", thread.getID());
        do {
            this.sleep(1L);
        } while (!EnmApplication.getSignal() && !thread.isCompleted() && !thread.isErrorOcurred() && !thread.isTimedOut() && (System.nanoTime() - startTime < 80L * Constants.TEN_EXP_9));

        if (thread.isErrorOcurred()) {
            logger.warn("ThreadId: {} Error occured in SHM",thread.getID());
        } else if(thread.isTimedOut()){
            logger.warn("ThreadId: {} Timeout occured in SHM",thread.getID());
        } else if (thread.isCompleted()) {
            logger.info("SHM ThreadId: {} completed!", thread.getID());
        } else {
            logger.info("SHM ThreadId: {} is still running", thread.getID());
        }
    }


    @Override
    public void cleanup() throws EnmException, HaTimeoutException {
        final List<SHMJob> shmJobList = viewJobs(null);
        if(deleteJobs(filterByJobsCompletedFailed(shmJobList))) {
            logger.info("all completed/failed jobs are deleted");
        } else {
            logger.info("all completed/failed jobs are not deleted");
        }
        if(deleteJobs(filterByJobsRunning(shmJobList))) {
            logger.info("all running jobs are deleted");
        } else {
            logger.warn("all running jobs are not deleted");
        }
        logger.info("output : {}", executeCliCommand("alarm ack -alobj \"ShmJobName : " + HA_BACKUP_JOB_ADMINISTRATOR + "\""));
    }

    private Predicate<SHMJob> getAllJobsRunning() {
        return shmJobCF -> "RUNNING".equalsIgnoreCase(shmJobCF.getJobStatus());
    }

    private Predicate<SHMJob> getAllJobsCompleted() {
        return shmJobCF -> "COMPLETED".equalsIgnoreCase(shmJobCF.getJobStatus());
    }

    private Predicate<SHMJob> getAllJobsFailed() {
        return shmJobCF -> "FAILED".equalsIgnoreCase(shmJobCF.getJobStatus());
    }

    private String createBackupJob() throws EnmException, HaTimeoutException {
        Object parse = null;
        try (final InputStream inputStream = SoftwareHardwareManagement.class.getResourceAsStream("/json/shmCreateBackupJob.json")) {
            parse = JSONValue.parse(inputStream);
        } catch (final IOException e) {
            logger.error("Failed to read jsonFile, root cause: ", e);
        }
        if (parse instanceof JSONObject) {
            final JSONObject jsonObject = prepareBody((JSONObject) parse);

            if (jsonObject != null) {
                final List<String[]> bodies = new ArrayList<>();
                bodies.add(new String[]{jsonObject.toString()});
                HttpResponse response;
                try {
                    response = getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, SHM_REST_URL);
                } catch (final IllegalStateException e) {
                    throw new HaTimeoutException(FAILED_TO_CREATE_BACKUP_JOB + ": " + e.getMessage(), EnmErrorType.APPLICATION);
                }
                if (response != null) {
                    analyzeResponse(FAILED_TO_CREATE_BACKUP_JOB, response);
                    final Object parseBody = JSONValue.parse(response.getBody());
                    if (parseBody instanceof JSONObject) {
                        return (String) ((JSONObject) parseBody).get("jobName");
                    } else {
                        logger.warn("Response body is {}", parseBody);
                    }
                } else {
                    logger.warn("For ActionType (POST) and action ({}) HttpResponse is NULL!", SHM_REST_URL);
                }
            }
        }
        return Constants.EMPTY_STRING;
    }

    private JSONObject prepareBody(final JSONObject parse) {
        final JSONObject jsonObject = parse;
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
                    if (prop instanceof JSONObject && ((String) ((JSONObject) prop).get("key")).contains("CV_NAME")) {
                        ((JSONObject) prop).put("value", "Backup_" + dateTimeStamp);
                    }
                }
            }
        }
        return jsonObject;
    }

    /**
     * Queries managed objects.
     *
     * @return the HTTP response's body from the REST call
     * @throws EnmException if failed to run the query
     */
    private List<SHMJob> viewJobs(final String jobName) throws EnmException, HaTimeoutException {
        final List<SHMJob> shmJobList = new ArrayList<>();
        Object parse = null;
        try (final InputStream inputStream = SoftwareHardwareManagement.class.getResourceAsStream("/json/shmViewJobs.json")) {
            parse = JSONValue.parse(inputStream);
        } catch (final IOException e) {
            logger.error("Failed to read jsonFile, root cause: ", e);
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
                        logger.info("object1 : {}", object);
                    } else {
                        object.put("filterText", HA_BACKUP_JOB_ADMINISTRATOR);
                        logger.info("object2 : {}", object);
                    }
                }
            }

            logger.info("Getting all details of SHM Backup Job {}....", jobName);

            final long startTime = System.nanoTime();
            do {
                shmJobList.clear();
                shmJobList.addAll(parseJobStatusJson(jsonObject));
                sleep(1L);
            } while (!EnmApplication.getSignal() && containsStatusRunning(shmJobList) && (System.nanoTime() - startTime < 120L *  Constants.TEN_EXP_9));

            logger.info("shmJobList : {}", shmJobList);

            final List<SHMJob> shJobsRunningList = filterByJobsRunning(shmJobList);
            if (!shJobsRunningList.isEmpty()) {
                logger.warn("the next jobs names are still running: ");
                shJobsRunningList.forEach(shmJob -> logger.warn("job Name: {}, job Id {}", shmJob.getName(), shmJob.getJobId()));
            }
        }
        return shmJobList;
    }

    private boolean containsStatusRunning(final List<SHMJob> shJobsList) {

        for (final SHMJob job : shJobsList) {
            if (job.getJobStatus().contains("RUNNING")) {
                return true;
            }
        }
        return false;
    }

    private List<SHMJob> filterByJobsCompletedFailed(final List<SHMJob> shJobsList) {
        final List<SHMJob> shmJobList = new ArrayList<>();
        shmJobList.addAll(shJobsList.parallelStream().
                filter(getAllJobsCompleted()).
                sequential().collect(Collectors.toList()));

        shmJobList.addAll(shJobsList.parallelStream().
                filter(getAllJobsFailed()).
                sequential().collect(Collectors.toList()));
        return shmJobList;
    }

    private List<SHMJob> filterByJobsRunning(final List<SHMJob> shJobsList) {
        final List<SHMJob> shmJobList = new ArrayList<>();

        shmJobList.addAll(shJobsList.parallelStream().
                filter(getAllJobsRunning()).
                sequential().collect(Collectors.toList()));

        return shmJobList;
    }

    private List<SHMJob> parseJobStatusJson(final JSONObject jsonObject) throws EnmException, HaTimeoutException {
        final HttpResponse response;
        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});
        final List<SHMJob> shmJobsList = new ArrayList<>();

        try {
            response = getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, VIEW_JOBS_URL);
        } catch (final IllegalStateException e) {
            throw new HaTimeoutException(FAILED_TO_VIEW_SHM_JOBS + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        if (response != null) {
            analyzeResponse(FAILED_TO_VIEW_SHM_JOBS, response);
            shmJobsList.addAll(getJobIdsAndStatus(response));
        } else {
            logger.warn("For ActionType (POST) and action ({}) HttpResponse is NULL!", VIEW_JOBS_URL);
            return shmJobsList;
        }

        return shmJobsList;
    }

    private List<SHMJob> getJobIdsAndStatus(final HttpResponse response) {
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
        return shmList;
    }

    private boolean deleteJobs(final List<SHMJob> shmJobList) throws EnmException, HaTimeoutException {
        final List<String> shmJobsIds = new ArrayList<>();
        if (shmJobList.isEmpty()) {
            logger.warn("Nothing to delete, job's list is empty.");
            return false;
        }

        final JSONArray jsonObject = new JSONArray();
        for (final SHMJob shmJob : shmJobList) {
            jsonObject.add(shmJob.getJobId());
            shmJobsIds.add(shmJob.getJobId());
        }

        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});

        final HttpResponse response;
        try {
            response = getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, DELETE_JOB_URL);
        } catch (final IllegalStateException e) {
            logger.warn("Illegal Exception found",e);
            throw new HaTimeoutException(String.format(FAILED_TO_DELETE_SHM_JOBS + ": " + e.getMessage(), shmJobsIds), EnmErrorType.APPLICATION);
        }
        if (response != null) {
            analyzeResponse(String.format(FAILED_TO_DELETE_SHM_JOBS, shmJobsIds), response);
            return response.getBody().contains("success");
        }
        return false;
    }

}
