package com.ericsson.nms.rv.core.shm;


import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.util.CommonUtils;
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
import java.util.stream.Collectors;

public class ShmSmrsThreadExecutor implements Runnable {

    private static final Logger logger = LogManager.getLogger(ShmSmrsThreadExecutor.class);
    private Thread worker;
    private final EnmApplication application;
    private boolean completed;
    private boolean errorOccurred;
    private long threadId;
    private final List<Node> nodes;
    private static int failedCounter = 0;
    private Date jobCreationDate;
    private long nanoTime;

    public boolean isCompleted() {
        return completed;
    }

    public boolean isErrorOcurred() {
        return errorOccurred;
    }


    public static Logger getLogger() {
        return logger;
    }

    ShmSmrsThreadExecutor(List<Node> nodes, EnmApplication application) {
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
        SHMJob shmJob;
        SHMJob shmRemoveBackupJob;
        try {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            final long dateTimeStamp = Long.parseLong(sdf.format(new Date()));
            final String jobName = createBackupJob("backup", dateTimeStamp);
            jobCreationDate = new Date();
            nanoTime = System.nanoTime();
            if (StringUtils.isNotEmpty(jobName)) {
                logger.info("ThreadId: {} ShmSmrs Backup Job command executed successfully, jobName : {}", threadId, jobName);
                shmJob = viewJobs(jobName);

                if (shmJob == null) {
                    logger.info("ThreadId: {} for jobName : {}, Unable to get job details.", threadId, jobName);
                    errorOccurred = true; //stop loop in parent.
                    return;
                } else {
                    if (shmJob.getJobResult().equalsIgnoreCase("FAILED")) {
                        throw new EnmException("Create-Backup job failed.");
                    } else if (!shmJob.getJobResult().equalsIgnoreCase("SUCCESS")) {
                        logger.info("ThreadId: {} ShmSmrs backup job not completed.", threadId);
                        completed = true; //stop loop in parent.
                        return;
                    }
                    logger.info("ThreadId: {} ShmSmrs Backup job's ({}) details successfully received.", threadId, jobName);
                    final List<SHMJob> shmJobList = new ArrayList<>();
                    shmJobList.add(shmJob);
                    if (deleteJobs(shmJobList)) {
                        logger.info("ThreadId: {} ShmSmrs Backup job ({}) deleted successfully.", threadId, jobName);
                    } else {
                        logger.warn("ThreadId: {} ShmSmrs Backup job ({}) is not deleted.", threadId, jobName);
                    }
                }

                logger.info("ThreadId: {}, ShmSmrs Removing backup file ...", threadId);
                final String removeJobName = createBackupJob("remove", dateTimeStamp);
                jobCreationDate = new Date();
                nanoTime = System.nanoTime();
                if (removeJobName == null || removeJobName.isEmpty()) {
                    application.stopAppDownTime(); // shm errors should not be added to smrs downtime.
                    application.setFirstTimeFail(true);
                    logger.info("ThreadId: {} for Remove-Backup file jobName : {}, Unable to get job details.", threadId, removeJobName);
                } else {
                    logger.info("ThreadId: {} ShmSmrs Remove-Backup file job's ({}) details successfully received.", threadId, removeJobName);
                    final List<SHMJob> shmRemoveJobList = new ArrayList<>();
                    shmRemoveBackupJob = viewJobs(removeJobName);
                    if (shmRemoveBackupJob == null) {
                        logger.info("ThreadId: {} for jobName : {}, Unable to get job details.", threadId, jobName);
                        errorOccurred = true; //stop loop in parent.
                        return;
                    } else if (shmRemoveBackupJob.getJobResult().equalsIgnoreCase("FAILED")) {
                        throw new EnmException("Remove-Backup job failed.");
                    } else if (!shmRemoveBackupJob.getJobResult().equalsIgnoreCase("SUCCESS")) {
                        logger.info("ThreadId: {} ShmSmrs backup job not completed.", threadId);
                        completed = true; //stop loop in parent.
                        return;
                    }
                    shmRemoveJobList.add(shmRemoveBackupJob);
                    if (deleteJobs(shmRemoveJobList)) {
                        logger.info("ThreadId: {} ShmSmrs Remove-Backup file job ({}) deleted successfully.", threadId, removeJobName);
                    } else {
                        logger.warn("ThreadId: {} ShmSmrs Remove-Backup file job ({}) is not deleted.", threadId, removeJobName);
                    }
                }
            } else {
                application.stopAppDownTime(); // shm errors should not be added to smrs downtime.
                application.setFirstTimeFail(true);
                logger.info("ThreadId: {} ShmSmrs failed in creating Backup Job. jobName : ({})", threadId, jobName);
            }
            completed = true;
        } catch (final Exception e) {
            errorOccurred = true;
            logger.warn("ThreadId: {} .. ShmSmrs failed : {}", threadId, e.getMessage());
            if (application.isFirstTimeFail()) {
                application.setFirstTimeFail(false);
            } else {
                application.startAppDownTime();
            }
        } finally {
            ShmSmrs.smrsNodeMap.put(nodes.get(0), true);
            Thread.currentThread().stop();
        }
    }

    private SHMJob viewJobs(final String jobName) {
        SHMJob shmJob;
        Object parse = null;
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
                    }
                }
            }
            logger.info("ThreadId: {} Creating ShmSmrs Backup Job {}...", threadId, jobName);
            final long createJobStartTime = System.currentTimeMillis();
            do {
                shmJob = parseJobStatusJson(jsonObject);
                CommonUtils.sleep(3);
            } while (!EnmApplication.getSignal() && shmJob == null && (System.currentTimeMillis() - createJobStartTime < 120L *  Constants.TEN_EXP_3));//timeout added in RTD-20152.

            if(shmJob != null) {
                logger.info("ThreadId: {} ShmSmrs Backup Job ({}) created successfully", threadId, shmJob);
            } else {
                logger.warn("ThreadId: {} ShmSmrs Backup Job ({}) is not created yet", threadId, jobName);
                return null;
            }

            logger.info("ThreadId: {} Waiting for ShmSmrs Backup Job {} to complete...", threadId, jobName);
            final long completeJobStartTime = System.nanoTime();
            do {
                shmJob = parseJobStatusJson(jsonObject);
                CommonUtils.sleep(3);
            } while (!EnmApplication.getSignal() && (System.nanoTime() - completeJobStartTime < 360L * Constants.TEN_EXP_9) && (shmJob == null || (!shmJob.getJobStatus().contains("COMPLETED") && !shmJob.getJobStatus().contains("FAILED"))));//timeout added in RTD-20152.

            logger.warn("ThreadId: {} ShmSmrs : ({}), Time : {} sec.", threadId, shmJob, (System.currentTimeMillis() - createJobStartTime)/Constants.TEN_EXP_3);
            if(shmJob !=  null) {
                logger.info("ThreadId: {} ShmSmrs JobResult: {} JobStatus: {}", threadId, shmJob.getJobResult(), shmJob.getJobStatus());
            }
            if(shmJob ==  null) {
                logger.warn("ThreadId: {} Unable to get details of ShmSmrs Backup Job ({})", threadId, jobName);
            } else if(shmJob.getJobResult().contains("FAILED")) {
                failedCounter++;
                logger.warn("ThreadId: {} ShmSmrs Job : ({}) failed ... count : {}", threadId, shmJob, failedCounter);
                printShmJobLogs(shmJob);
            } else if(shmJob.getJobResult().contains("SUCCESS")) {
                application.stopAppDownTime(nanoTime, jobCreationDate);  //stop DT with job start date.
                application.setFirstTimeFail(true);
                logger.info("ThreadId: {} ShmSmrs : ({}) Completed successfully.", threadId, shmJob);
            } else if(shmJob.getJobStatus().contains("RUNNING")) {
                logger.warn("ThreadId: {} ShmSmrs : ({}) is still running.", threadId, shmJob);
                printShmJobLogs(shmJob);
            } else if(shmJob.getJobStatus().contains("CREATED")) {
                logger.warn("ThreadId: {} ShmSmrs : ({}) is not started yet", threadId, shmJob);
                printShmJobLogs(shmJob);
            } else {
                logger.info("ThreadId: {}, Unhandled Job/result, ShmSmrs job: ({})", threadId, shmJob);
                printShmJobLogs(shmJob);
            }
            return shmJob;
        }
        return null;
    }

    private void printShmJobLogs(final SHMJob job) {
        final String jobId = job.getJobId();
        final List<String[]> bodies = new ArrayList<>();
        final String uri = "/oss/shm/rest/job/joblog";
        final StringBuilder logBuilder = new StringBuilder();
        final String node = nodes.get(0).getNetworkElementId();
        try {
            final JSONObject jsonPayload = (JSONObject) JSONValue.parse("{offset: 1,limit: 20,sortBy: entryTime,orderBy: desc,neJobIds: '',logLevel: INFO,filterDetails: [],mainJobId: "+ jobId +"}");
            logger.info("jsonPayload : {}", jsonPayload.toString());
            bodies.add(new String[]{jsonPayload.toString()});
            final HttpResponse response = application.getHttpRestServiceClient().sendPostRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, uri);
            logger.info("ThreadId: {}, Get log responseCode: {}", threadId, response.getResponseCode().getCode());
            final JSONObject jsonResponse = (JSONObject) JSONValue.parse(response.getContent());
            final JSONArray logArray = (JSONArray) jsonResponse.get("result");
            if (logArray != null ) {
                logBuilder.append("===================================\n");
                for (final Object obj : logArray) {
                    final JSONObject jsonObject = (JSONObject) obj;
                    String msg = jsonObject.get("message").toString();
                    logBuilder.append(msg);
                    logBuilder.append("\n");
                }
                logBuilder.append("===================================");
            }
            logger.warn("ThreadId: {}, JobId: {}, JobName: {}, Node: {}, JobLog: \n{}", threadId, jobId, job.getName(), node, logBuilder.toString());
        } catch (final Exception e) {
            logger.warn("ThreadId: {}, Job logging failed : {}", threadId, e.getMessage());
        }
    }

    private String createBackupJob(final String jobType, final long dateTimeStamp) {
        Object parse;
        final String FAILED_TO_CREATE_BACKUP_JOB = "Failed to create ShmSmrs Backup Job";
        final String SHM_REST_URL = "/oss/shm/rest/job";
        final String backupJson;
        final String nodeType = nodes.get(0).getNodeType();
        if (jobType.contains("backup")) {
            if (nodeType.equalsIgnoreCase(NodeType.LTE.getType())) {
                backupJson = "/json/shmSmrsCreateBackupJob.json";
            } else {
                backupJson = "/json/shmSmrsCreateBackupJobForRadionode.json";
            }
        } else {
            if (nodeType.equalsIgnoreCase(NodeType.LTE.getType())) {
                backupJson = "/json/shmSmrsDeleteBackupJob.json";
            } else {
                backupJson = "/json/shmSmrsDeleteBackupJobForRadionode.json";
            }
        }
        try (final InputStream inputStream = ShmThreadExecutor.class.getResourceAsStream(backupJson)) {
            parse = JSONValue.parse(inputStream);
        } catch (final IOException e) {
            logger.error("ThreadId: {} Failed to read jsonFile, root cause: ", threadId, e);
            return "";
        }
        try {
            if (parse instanceof JSONObject) {
                final JSONObject jsonObject = prepareBody((JSONObject) parse, jobType, dateTimeStamp);
                if (jsonObject != null) {
                    final List<String[]> bodies = new ArrayList<>();
                    bodies.add(new String[]{jsonObject.toString()});
                    final HttpResponse response = application.getHttpRestServiceClient().sendPostRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, SHM_REST_URL);
                    if (response != null) {
                        application.analyzeResponseWithoutStopAppDownTime(FAILED_TO_CREATE_BACKUP_JOB, response);//print response
                        final Object parseBody = JSONValue.parse(response.getBody());
                        logger.info("ThreadId: {} parseBody : {}", threadId, parseBody);
                        if (parseBody instanceof JSONObject) {
                            return (String) ((JSONObject) parseBody).get("jobName");
                        } else {
                            logger.warn("ThreadId: {}, ShmSmrs create job response is not JSON : {}", threadId, parseBody);
                        }
                    } else {
                        logger.warn("ThreadId: {} For ShmSmrs ActionType (POST) and action ({}) HttpResponse is NULL!",threadId, SHM_REST_URL);
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("ThreadId: {} ShmSmrs create backup job failed : {}", threadId, ex.getMessage());
        }
        return Constants.EMPTY_STRING;
    }

    private JSONObject prepareBody(final JSONObject parse, final String jobType, final long dateTimeStamp) {
        final String HA_BACKUP_JOB_ADMINISTRATOR = "HA_"+ jobType +"_Job_";
        final String jobName = HA_BACKUP_JOB_ADMINISTRATOR + dateTimeStamp;
        parse.put("name", jobName);
        String nodeName = "";

        final JSONArray neNames = new JSONArray();
        final List<String> neNamesList = nodes.stream().map(Node::getNetworkElementId).collect(Collectors.toList());
        for (final String neName : neNamesList) {
            final JSONObject object = new JSONObject();
            object.put("name", neName);
            neNames.add(object);
            nodeName = neName;
        }
        final Node node = nodes.get(0);
        parse.put("neNames", neNames);

        try {
            final JSONArray configurations = (JSONArray) (parse).get("configurations");
            if (jobType.contains("backup")) {
                for (final Object config : configurations) {
                    if (config instanceof JSONObject && ((JSONObject) config).containsKey("neType")) {
                        final JSONArray properties = (JSONArray) ((JSONObject) config).get("properties");
                        for (final Object prop : properties) {
                            if (prop instanceof JSONObject && (((String) ((JSONObject) prop).get("key")).contains("CV_NAME") || ((String) ((JSONObject) prop).get("key")).equalsIgnoreCase("BACKUP_NAME"))) {
                                ((JSONObject) prop).put("value", "Hatest_backup_file_" + dateTimeStamp);
                            }
                        }
                    }
                }
            } else { //delete backup file.
                for (final Object config : configurations) {
                    if (config instanceof JSONObject && ((JSONObject) config).containsKey("neType")) {
                        final JSONArray neProperties = (JSONArray) ((JSONObject) config).get("neProperties");
                        for (final Object prop : neProperties) {
                            if (((String) ((JSONObject) prop).get("neNames")).contains("NodeName")) {
                                ((JSONObject) prop).put("neNames", nodeName);
                            }
                            final JSONArray properties = (JSONArray) ((JSONObject) prop).get("properties");
                            for (final Object obj : properties) {
                                if (obj instanceof JSONObject && (((String) ((JSONObject) obj).get("key")).equalsIgnoreCase("CV_NAME") ||
                                        ((String) ((JSONObject) obj).get("key")).equalsIgnoreCase("BACKUP_NAME"))) {
                                    if (node.getNodeType().equalsIgnoreCase(NodeType.LTE.getType())) {
                                        ((JSONObject) obj).put("value", "Hatest_backup_file_" + dateTimeStamp + ".zip|ENM,Hatest_backup_file_" + dateTimeStamp + "|NODE");
                                    } else {
                                        final String fileName = getBackUpFileName(nodeName, dateTimeStamp);
                                        if (!fileName.isEmpty()) {
                                            ((JSONObject) obj).put("value", fileName + "|null|null|ENM,Hatest_backup_file_" + dateTimeStamp + "|System|Systemdata|NODE");
                                        } else {
                                            logger.warn("ThreadId: {}, Failed to get backup fileName.", threadId);
                                            return null;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("ThreadId: {}, prepareBody Message : {}", threadId, e.getMessage());
            e.printStackTrace();
        }
        logger.info("ThreadId: {}, prepareBody jsonObject : {}", threadId, parse.toJSONString());
        return parse;
    }

    private String getBackUpFileName(final String nodeName, final long dateTimeStamp) {
        String fileName = "";
        HttpResponse responseJson = null;
        try {
            final String backupFile = "_Hatest_backup_file_" + dateTimeStamp;
            final List<String[]> bodies = new ArrayList<>();
            final Object parse = JSONValue.parse("{fdns: [NetworkElement=" + nodeName + "]}");
            final JSONObject jsonObject = (JSONObject) parse;
            bodies.add(new String[]{jsonObject.toString()});
            HttpResponse response = application.getHttpRestServiceClient().sendPostRequest(100, ContentType.APPLICATION_JSON,
                    new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, "/oss/shm/rest/inventory/backupInventory");
            logger.info("ThreadId: {}, Get Backup Inventory response : {}", threadId, response.getBody());
            final JSONObject jsonResponse = (JSONObject) JSONValue.parse(response.getContent());
            logger.info("ThreadId: {}, Inventory ID : {}", threadId, jsonResponse.get("requestId"));
            String requestId = jsonResponse.get("requestId").toString();
            CommonUtils.sleep(2);
            final Object parseData = JSONValue.parse("{offset: 1,limit: 50,sortBy: date,ascending: false,filterDetails: [],requestId: " + requestId + "}");
            logger.info("parseData : {}", parseData);
            final JSONObject jsonData = (JSONObject) parseData;
            final List<String[]> bodiesData = new ArrayList<>();
            bodiesData.add(new String[]{jsonData.toString()});
            final long startTime = System.currentTimeMillis();
            do {
                responseJson = application.getHttpRestServiceClient().sendPostRequest(100, ContentType.APPLICATION_JSON,
                        new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodiesData, null, "/oss/shm/rest/inventory/v1/backupItems");
                logger.info("ThreadId: {}, Get backupItems response code: {}", threadId, responseJson.getResponseCode().getCode());
                CommonUtils.sleep(1);
            } while (responseJson.getResponseCode() != HttpStatus.OK && (System.currentTimeMillis() - startTime) < 30L * (long) Constants.TEN_EXP_3);

            final JSONObject jsonResponseData = (JSONObject) JSONValue.parse(responseJson.getContent());
            final JSONArray array = (JSONArray) jsonResponseData.get("backupItemList");
            for (final Object fileObject : array) {
                final JSONObject obj = (JSONObject) fileObject;
                final String name = obj.get("name").toString();
                if (name.contains(backupFile)) {
                    fileName = obj.get("fileName").toString();
                    logger.info("fileName :: {}", fileName);
                    break;
                }
            }
        } catch (final Exception e) {
            logger.warn("ThreadId: {}, Failed to get backup file name for radio node: {}", threadId, e.getMessage());
        }
        logger.info("ThreadId: {}, fileName : {}", threadId, fileName);
        if (fileName.isEmpty()) {
            logger.warn("ThreadId: {}, Backup File name is empty.", threadId);
            logger.warn("ThreadId: {}, Backup File name ResponseBody: {}", threadId, responseJson != null ? responseJson.getBody() : null);
        }
        return fileName;
    }

    private SHMJob parseJobStatusJson(final JSONObject jsonObject) {
        final String FAILED_TO_VIEW_SHM_JOBS = "Failed to view ShmSmrs jobs";
        final String VIEW_JOBS_URL = "/oss/shm/rest/job/v2/jobs";
        final List<String[]> bodies = new ArrayList<>();
        bodies.add(new String[]{jsonObject.toString()});
        HttpResponse response;

        try {
            response = application.getHttpRestServiceClient().sendPostRequest(EnmApplication.TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{EnmApplication.Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, VIEW_JOBS_URL);
            if (response != null) {
                application.analyzeResponseWithoutStopAppDownTime(FAILED_TO_VIEW_SHM_JOBS, response);
                return getJobIdsAndStatus(response);
            } else {
                logger.warn("ThreadId: {} For ActionType (POST) and action ({}) HttpResponse is NULL!", threadId, VIEW_JOBS_URL);
            }
        } catch (final Exception e) {
            logger.warn("ThreadId: {} ShmSmrs parseJobStatusJson failed: {}", threadId, e.getMessage());
        }
        return null;
    }

    private boolean deleteJobs(final List<SHMJob> shmJobList) {
        final List<String> shmJobsIds = new ArrayList<>();
        final String DELETE_JOB_URL = "/oss/shm/rest/job/delete";
        final String FAILED_TO_DELETE_SHM_JOBS = "Failed to delete SHM job : %s";
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
            if (response != null) {
                application.analyzeResponseWithoutStopAppDownTime(String.format(FAILED_TO_DELETE_SHM_JOBS, shmJobsIds), response);
                return response.getBody().contains("success");
            } else {
                logger.warn("ThreadId: {} For ShmSmrs url ({}) HttpResponse is NULL!", threadId, DELETE_JOB_URL);
            }
        } catch (Exception e) {
            logger.warn("ThreadId: {} ShmSmrs delete job failed: {}", threadId, e.getMessage());
        }
        return false;
    }

    private SHMJob getJobIdsAndStatus(final HttpResponse response) {
        logger.info("ThreadId: {} Status response: {}", threadId, response.getBody());
        final List<SHMJob> shmList = new ArrayList<>();

        final Object body = JSONValue.parse(response.getBody());
        if (body instanceof JSONObject) {
            final Object result = ((JSONObject) body).get("result");
            if (result instanceof JSONArray) {
                for (final Object o : (JSONArray) result) {
                    if (o instanceof JSONObject) {
                        final JSONObject object = (JSONObject) o;
                        shmList.add(new SHMJob(object.getAsString("jobName"), object.getAsString("jobId"),
                                object.getAsString("status"), object.getAsString("result")));
                    }
                }
            }
        }
        return (shmList.isEmpty()) ? null : shmList.get(0);
    }

}
