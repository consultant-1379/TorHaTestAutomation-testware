package com.ericsson.nms.rv.core.shm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;


import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.cifwk.taf.data.Ports;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.netsimhandler.NodeType;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.HaTimeoutException;

import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
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
public final class ShmSmrs extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(SoftwareHardwareManagement.class);
    private static final String SHM_REST_URL = "/oss/shm/rest/job";
    private static final String VIEW_JOBS_URL = SHM_REST_URL + "/v2/jobs";
    private static final String DELETE_JOB_URL = SHM_REST_URL + "/delete";

    private static final String FAILED_TO_VIEW_SHM_JOBS = "Failed to view ShmSmrs jobs";
    private static final String FAILED_TO_DELETE_SHM_JOBS = "Failed to delete ShmSmrs job";
    private static boolean readyToVerify = false;
    private final List<Node> nodes = new ArrayList<>();
    public static final HashMap<Node, Boolean> smrsNodeMap = new HashMap<>();

    public ShmSmrs(final SmrsLoadGenerator smrsLoadGenerator, final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
        nodes.addAll(smrsLoadGenerator.getShmSmrsVerificationNodes());
        if (!nodes.isEmpty()) {
            readyToVerify = true;
            for (final Node node: nodes) {
                smrsNodeMap.put(node, true);
            }
            logger.info("smrsNodeMap : {}", smrsNodeMap);
        }
        logger.info("ShmSmrs is readToVerify {}", readyToVerify);
        logger.info("ShmSmrs Using nodes {}", getUsedNodes(nodes));
    }

    List<Node> getAvailableNode() {
        final List<Node> node = new ArrayList<>();
        logger.info("Getting available nodes ...");
        for (final Map.Entry<Node, Boolean> entry : smrsNodeMap.entrySet()) {
            if (entry.getValue()) {
                node.add(entry.getKey());
                smrsNodeMap.put(entry.getKey(), false);
                logger.info("Node : {} is available.", entry.getKey().getNetworkElementId());
                break;
            }
        }
        if (node.isEmpty()) {
            logger.warn("All nodes are occupied.");
        }
        logger.info("smrsNodeMap : {}", smrsNodeMap);
        return node;
    }

    @Override
    public void verify() {
        final List<Node> node = getAvailableNode();
        if (!readyToVerify || node.isEmpty()) {
            return;
        }
        logger.info("ShmSmrs Verifier Running ...");
        ShmSmrsThreadExecutor thread = new ShmSmrsThreadExecutor(node, this);
        thread.execute();
        long startTime = System.currentTimeMillis();
        logger.info("ShmSmrs ThreadId: {} started.", thread.getID());
        do {
            this.sleep(1L);
        } while (!EnmApplication.getSignal() && !thread.isCompleted() && !thread.isErrorOcurred() && (System.currentTimeMillis() - startTime < 100L * Constants.TEN_EXP_3));

        if (thread.isErrorOcurred()) {
            logger.warn("ThreadId: {} Error occured in ShmSmrs",thread.getID());
        } else if (thread.isCompleted()) {
            logger.info("ShmSmrs ThreadId: {} completed!", thread.getID());
        } else {
            logger.info("ShmSmrs ThreadId: {} is still running", thread.getID());
        }
    }


    @Override
    public void cleanup() throws EnmException, HaTimeoutException {
        final List<String> jobNameList = new ArrayList<>();
        jobNameList.add("HA_backup_Job_");
        jobNameList.add("HA_remove_Job_");
        for (String jobName : jobNameList) {
            final List<SHMJob> shmJobList = viewJobs(jobName);
            if (deleteJobs(filterByJobsCompletedFailed(shmJobList))) {
                logger.info("all completed/failed jobs are deleted");
            } else {
                logger.info("all completed/failed jobs are not deleted");
            }
            if (deleteJobs(filterByJobsRunning(shmJobList))) {
                logger.info("all running jobs are deleted");
            } else {
                logger.warn("all running jobs are not deleted");
            }
            logger.info("output : {}", executeCliCommand("alarm ack -alobj \"ShmJobName : " + jobName + "\""));
        }
        try {
            cleanUpSmrsFiles();
        } catch (final Exception e) {
            logger.info("Cleanup smsrs file got exception: {}", e.getMessage());
        }
    }

    private void cleanUpSmrsFiles() {
        if (nodes.isEmpty()) {
            return;
        }
        final Node node = nodes.get(0);
        String nodeType = "";
        if (node.getNodeType().equalsIgnoreCase(NodeType.RADIO.getType())) {
            nodeType = "radionode";
        } else if (node.getNodeType().equalsIgnoreCase(NodeType.LTE.getType())) {
            nodeType = "erbs";
        }
        final String finalNodeType = nodeType;

        if (HAPropertiesReader.isEnvCloudNative()) {
            smrsNodeMap.forEach((smrsNode, state) -> {
                final String uri = "watcher/adu/smrs/remove/" + finalNodeType + ":" + smrsNode.getNetworkElementId();
                final HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpPostResponse(uri);
                logger.info("Cleanup response code: {}", httpResponse.getResponseCode().getCode());
            });
        } else if (HAPropertiesReader.isEnvCloud()) {
            String getHost = "consul members | grep filetransfer | head -n 1 | /bin/awk -F ' ' '{print $1}'";
            final CliShell MS_SHELL = new CliShell(HAPropertiesReader.getMS());
            CliResult result = MS_SHELL.execute(getHost);
            String ftsHostName = result.getOutput().trim();
            logger.info("ftsHostName : {}", ftsHostName);
            Host ftsHost = new Host();
            ftsHost.setHostname(ftsHostName);
            ftsHost.setIp(ftsHostName);
            ftsHost.setUser("cloud-user");
            ftsHost.setType(HostType.JBOSS);
            Map<Ports, String> port = new HashMap<>();
            port.put(Ports.SSH, "22");
            ftsHost.setPort(port);
            final CliShell FTS_SHELL = new CliShell(ftsHost);
            smrsNodeMap.forEach((smrsNode, state) -> {
                final String rmCommand = String.format("sudo rm -rf /home/smrs/smrsroot/backup/%s/%s/*Hatest_backup_file_*", finalNodeType, smrsNode.getNetworkElementId());
                logger.info("Cleaning : {} node {} backup files ...", finalNodeType, smrsNode.getNetworkElementId());
                CliResult ftsResult = FTS_SHELL.execute(rmCommand);
                if (ftsResult.isSuccess()) {
                    logger.info("Backup Files deleted successfully on : {}", smrsNode.getNetworkElementId());
                } else {
                    logger.info("Failed to delete Backup Files on : {}", smrsNode.getNetworkElementId());
                }
            });
        } else {
            final Host fileHost = HostConfigurator.getAllHosts("filetransferservice").get(0);
            logger.info("fileHost name : {}", fileHost.getHostname());
            logger.info("fileHost ip : {}", fileHost.getIp());
            fileHost.setUser("cloud-user");
            final CliShell FTS_SHELL = new CliShell(fileHost);
            smrsNodeMap.forEach((smrsNode, state) -> {
                final String rmCommand = String.format("sudo rm -rf /home/smrs/smrsroot/backup/%s/%s/*Hatest_backup_file_*", finalNodeType, smrsNode.getNetworkElementId());
                logger.info("Cleaning : {} node {} backup files ...", finalNodeType, smrsNode.getNetworkElementId());
                CliResult result = FTS_SHELL.execute(rmCommand);
                logger.info("Result : {}", result.getOutput());
                if (result.isSuccess()) {
                    logger.info("Backup Files deleted successfully on : {}", smrsNode.getNetworkElementId());
                } else {
                    logger.info("Failed to delete Backup Files on : {}", smrsNode.getNetworkElementId());
                }
            });
        }
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
                    }
                }
            }

            logger.info("Getting all details of ShmSmrs Backup Job {}....", jobName);

            final long startTime = System.nanoTime();
            do {
                shmJobList.clear();
                shmJobList.addAll(parseJobStatusJson(jsonObject));
                sleep(1L);
            } while (!EnmApplication.getSignal() && containsStatusRunning(shmJobList) && (System.nanoTime() - startTime < 120L *  Constants.TEN_EXP_9));

            logger.info("ShmSmrs JobList : {}", shmJobList);

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
