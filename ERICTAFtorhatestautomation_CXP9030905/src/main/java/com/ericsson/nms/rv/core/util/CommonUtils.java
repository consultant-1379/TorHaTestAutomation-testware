package com.ericsson.nms.rv.core.util;

import static com.ericsson.cifwk.taf.scenario.TestScenarios.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeoutException;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.cifwk.taf.data.User;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerificationTasks;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.scenario.TestScenario;
import com.ericsson.cifwk.taf.scenario.TestScenarioRunner;
import com.ericsson.cifwk.taf.scenario.api.ScenarioExceptionHandler;
import com.ericsson.cifwk.taf.scenario.api.ScenarioListener;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.ScenarioLogger;
import com.ericsson.nms.rv.core.downtime.DownTimeHandler;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.core.upgrade.UpgradeVerifier;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class CommonUtils {
    private static final Logger logger = LogManager.getLogger(CommonUtils.class);
    private static final long SESSION_TIME = HAPropertiesReader.getHttpSessionTimeout();
    public static Map<String,String> txtFiles = new HashMap<>();
    public static Map<String,String> dataFiles = new HashMap<>();
    public static boolean isFileCopyFailed = false;

    public static void executeUntilSignal(final EnmApplication enmApplication, final String appName) {
        long startTime = System.nanoTime();
        boolean doLogin = true;
        while (!EnmApplication.getSignal()) {
            if (enmApplication.getSystemStatus().isSSOAvailable() ||
                    (enmApplication instanceof SystemVerifier) ||
                    (enmApplication instanceof NbiAlarmVerifier) ||
                    (enmApplication instanceof NbiCreateSubsVerifier)) {
                try {
                    boolean isVerifierPossible = true;
                    final String cause = "HTTP session has expired trying to do login again";
                    if ((System.nanoTime() - startTime) > SESSION_TIME) {
                        logger.info(cause);
                        isVerifierPossible = tryToLogin(enmApplication, cause);
                        if (!isVerifierPossible) {
                            logger.error("Session has expired, Verifier cannot run due to login has failed, trying to login again");
                        }

                        startTime = System.nanoTime();
                    } else if (doLogin || enmApplication.isLoginNeed()) {
                        isVerifierPossible = tryToLogin(enmApplication, "");
                        if (!isVerifierPossible) {
                            logger.error(" Verifier cannot run due login has failed, trying to login again");
                        }

                        logger.info("Login is available,starting application verifier ");
                        doLogin = false;
                        startTime = System.nanoTime();
                        enmApplication.cleanLoginFailEvent();
                    }

                    if (isVerifierPossible) {
                        enmApplication.verify();
                    } else {
                        doLogin = true;
                    }

                } catch (final EnmException e) {
                    logger.warn(appName, e);
                    try {
                        enmApplication.login();
                        if (enmApplication.isFirstTimeFail()) {
                            enmApplication.setFirstTimeFail(false);
                        } else {
                            enmApplication.stopAppTimeoutDownTime();
                            enmApplication.startDowntime(e.getEnmErrorType());
                        }
                    } catch (final EnmException e1) {
                        logger.warn(appName, e1.getMessage());
                        logger.warn(appName, e1);
                    }
                    enmApplication.doLogin();
                } catch (final TimeoutException te) {
                    logger.warn(appName, te.getMessage());
                    logger.warn(appName, te);
                    EnmApplication.startSystemDownTime();
                } catch (final HaTimeoutException t) {
                    logger.warn(appName, t.getMessage());
                    logger.warn(appName, t);
                    try {
                        enmApplication.login();
                        enmApplication.stopAppDownTime();
                        enmApplication.startAppTimeoutDowntime();
                    } catch (final EnmException e2) {
                        logger.warn(appName, e2.getMessage());
                        logger.warn(appName, e2);
                    }
                    enmApplication.doLogin();
                } catch (final Exception e) {
                    logger.error("Problem during verifier execution", e);
                } finally {
                    enmApplication.sleep(2L);
                }
            } else {
                logger.error("Login answer not recieved, stopping application verifier, waiting for Login success");
                final Class<? extends EnmApplication> clazz = enmApplication.getClass();
                final DownTimeHandler downTimeHandler = EnmApplication.getDownTimeAppHandler().get(clazz);
                if(downTimeHandler.getDowntimeStarted()) {
                    enmApplication.stopAppDownTime();
                } else if (downTimeHandler.getAppTimeoutStarted()) {
                    enmApplication.stopAppTimeoutDownTime();
                }
                enmApplication.sleep(1L);
                doLogin = true;
                startTime = System.nanoTime();
            }
        }
    }

    public static boolean tryToLogin(final EnmApplication enmApplication, final String cause) {
        logger.info("trying to do login due {} ", cause);
        boolean isLoginSuccess = true;
        try {
            enmApplication.login();
        } catch (final Exception e) {
            logger.warn("Login failed, retrying login before starting downtime...");
            try {
                enmApplication.login();
            } catch (final Exception ex) {
                logger.warn("login/logout failed ", ex);
                isLoginSuccess = false;
            }
        }
        return isLoginSuccess;
    }

    public static boolean predictFunctionalArea(final FunctionalArea area) {
        final String appString = area.get();
        if (appString.equalsIgnoreCase(FunctionalArea.AMOSHOST.get())) {
            if (!(HAPropertiesReader.isEnvCloud() || HostConfigurator.getSCP1() != null || HAPropertiesReader.isAmosOnSVC())) {
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(FunctionalArea.AMOS.get()), HAPropertiesReader.appMap.get(FunctionalArea.AMOS.get()) + ", is disabled as there is no Scripting cluster on this deployment");
                return false;
            }
        } else if (!HAPropertiesReader.getFunctionalAreasMap().get(appString)) {
            if (HAPropertiesReader.isEnvCloudNative() && (appString.equalsIgnoreCase(FunctionalArea.ESM.get()))) {
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(appString), "Feature not available in cENM");
            } else {
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(appString), HAPropertiesReader.appMap.get(appString) + ", Disabled in ADU Job");
            }
            return false;
        }
        return true;
    }

    public static void runScenario(final TestScenario scenario, final ScenarioListener logger) {
        final TestScenarioRunner runner = runner().withListener(logger)
                .withDefaultExceptionHandler(ScenarioExceptionHandler.LOGONLY).build();
        runner.start(scenario);
        ((ScenarioLogger) logger).verify();
    }

    public static void printDownTime(final long downtime, final long deltaThreshold, final String msg) {
        if (downtime > 0) {
            if (downtime > deltaThreshold) {
                logger.error(msg);
            } else {
                logger.warn(msg);
            }
        } else {
            logger.info(msg);
        }
    }

    public static void sleep(final int timeInSeconds) {
        try {
            Thread.sleep((long) timeInSeconds * (long) Constants.TEN_EXP_3);
        } catch (final InterruptedException e) {
            logger.warn("{} sec sleep interrupted.", timeInSeconds, e);
        }
    }

    public static void uploadFileOnHost(final Host host, final String remoteDir, final File file) {
        try {
            final JSch jsch = new JSch();
            logger.info("Uploading file to IP : {}, directory : {}, File : {}", host.getIp(), remoteDir, file.getName() );
            final Session session = jsch.getSession(host.getUser(), host.getIp(), 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(host.getPass());
            session.connect();
            final Channel channel = session.openChannel("sftp");
            channel.connect();
            final ChannelSftp sftpChannel = (ChannelSftp) channel;
            sftpChannel.cd(remoteDir);
            logger.info("Copying file to : {}/{}", remoteDir, file.getName());
            final FileInputStream fis = new FileInputStream(file);
            sftpChannel.put(fis, file.getName(), FTP.BINARY_FILE_TYPE);
            fis.close();
            logger.info("disconnecting...");
            sftpChannel.disconnect();
            channel.disconnect();
            session.disconnect();
            logger.info("File {} uploaded successfully.", file.getName());
        } catch (final Exception e ) {
            logger.warn("Upload file error : {}", e.getMessage());
        }
    }

    private static void uploadFileOnSlave(final Host host, final String remoteDir, final File file) {
        try {
            final JSch jsch = new JSch();
            final String dataDir = "TestData";
            logger.info("Uploading file to IP : {}, directory : {}, File : {}", host.getIp(), remoteDir, file.getName() );
            final Session session = jsch.getSession(host.getUser(), host.getIp(), 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(host.getPass());
            session.connect();
            final Channel channel = session.openChannel("sftp");
            channel.connect();
            final ChannelSftp sftpChannel = (ChannelSftp) channel;
            sftpChannel.cd(remoteDir);
            try {
                sftpChannel.mkdir(dataDir);
            } catch (final Exception e) {
                //Dir exist.
            }
            sftpChannel.cd(dataDir);
            logger.info("Copying file to : {}/TestData/{}", remoteDir, file.getName());
            final FileInputStream fis = new FileInputStream(file);
            sftpChannel.put(fis, file.getName());

            fis.close();
            logger.info("disconnecting...");
            sftpChannel.disconnect();
            channel.disconnect();
            session.disconnect();
            logger.info("File {} uploaded successfully.", file.getName());
        } catch (final Exception e ) {
            logger.warn("Upload file error : {}", e.getMessage());
        }
    }

    public static void writeFileToSlave(String fileName, InputStream stream) {
        final String workSpacePath = HAPropertiesReader.getProperty("env.workspace.dir", "/tmp");
        logger.info("workSpacePath : {}",  workSpacePath);
        final String tempFileName = "/tmp/" + fileName;
        final Host host =  DataHandler.getHostByType(HostType.GATEWAY);
        final File file = new File(tempFileName);
        try {
            if (file.exists() && file.delete()) {
                logger.info("Temp file {} cleaned.", file.getName());
            }
            if (file.createNewFile()) {
                logger.info("Temp file {} created successfully. path : {}", file.getName(), file.getAbsolutePath());
            }
            FileUtils.copyInputStreamToFile(stream, file);
            uploadFileOnSlave(host, workSpacePath, file);
            if (file.exists() && file.delete()) {
                logger.info("Temp file {} deleted.", file.getName());
            }
        } catch (final Exception e) {
            logger.warn(e.getMessage());
        }
    }

    public static void writeResultToWorkLoadVM(String result) {
        try{
            final Host host = HAPropertiesReader.getWorkload();
            if (host == null) {
                return;
            }
            logger.info("Writing result to workload VM");
            final String workSpacePath = "/home/enmutils/apt/adu_reports";
            String deploymentHostName;
            String username = "taf_user";
            String password = "super_secret";
            if (HAPropertiesReader.isEnvCloud()) {
                deploymentHostName = HAPropertiesReader.getTafConfigDitDeploymentName();
            } else if (HAPropertiesReader.isEnvCloudNative()) {
                deploymentHostName = HAPropertiesReader.getProperty("cluster_id", "");
            } else {
                deploymentHostName = HAPropertiesReader.getTafClusterId();
            }
            if (UpgradeVerificationTasks.isUpgradeFailed()) {
                deploymentHostName = deploymentHostName.concat("-[FAIL]");
            } else {
                deploymentHostName = deploymentHostName.concat("-[PASS]");
            }
            String ugFinishTime = UpgradeVerifier.upgradeFinishTimeCopy;
            Date originalDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(ugFinishTime);
            String formattedDate = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(originalDate);
            final String fileName = deploymentHostName + "-" + formattedDate + ".html";
            final String tempFileName = "/tmp/" + fileName;

            logger.info("uploading ADU results to Workload VM");
            logger.info("WorkSpacePath : {}", workSpacePath);
            logger.info("filename : {}", fileName);
            logger.info("Hostname : {}, Host type : {}, Host IP : {}.", host.getHostname(), host.getType(), host.getIp());

            final File file = new File(tempFileName);

            if (file.exists() && file.delete()) {
                logger.info("Temp file {} cleaned.", file.getName());
            }
            if (file.createNewFile()) {
                logger.info("Temp file {} created successfully. path : {}", file.getName(), file.getAbsolutePath());
            }
            try(FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(result.getBytes());
            } catch (IOException e2){
                logger.warn(e2.getMessage());
            }

            if(!uploadOnWorkLoadVM(username, password, host.getIp(), workSpacePath, file)) {
                username = "root";
                password = "12shroot";
                if (!uploadOnWorkLoadVM(username, password, host.getIp(), workSpacePath, file)) {
                    for (User user : host.getUsers()) {
                        username = user.getUsername();
                        password = user.getPassword();
                        if (uploadOnWorkLoadVM(username, password, host.getIp(), workSpacePath, file)) {
                            break;
                        }
                    }
                }
            }

            if (file.exists() && file.delete()) {
                logger.info("Temp file {} deleted.", file.getName());
            }
        } catch (final Exception e3) {
            logger.warn(e3.getMessage());
            e3.printStackTrace();
        }
    }

    private static boolean uploadOnWorkLoadVM(final String username, final String password, final String ip, final String remoteDir, final File file) {
        try {
            final JSch jsch = new JSch();
            logger.info("Trying login to IP : {}", ip);
            final Session session = jsch.getSession(username, ip, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(password);
            logger.info("with username : {}, password : {}", username, password);
            session.connect();
            final Channel channel = session.openChannel("sftp");
            channel.connect();
            final ChannelSftp sftpChannel = (ChannelSftp) channel;
            logger.info("Login success");

            logger.info("Remove files older than 7 days.");
            removeOlderFiles(sftpChannel, remoteDir);

            final FileInputStream fis = new FileInputStream(file);
            logger.info("Opening directory : {}", remoteDir);
            sftpChannel.cd(remoteDir);

            logger.info("Copying file to : {}/{}", remoteDir, file.getName());
            sftpChannel.put(fis, file.getName());

            fis.close();
            logger.info("disconnecting...");
            sftpChannel.disconnect();
            channel.disconnect();
            session.disconnect();
            logger.info("File {} uploaded successfully.", file.getName());
        } catch (final Exception e ) {
            logger.warn("Upload file error : {}", e.getMessage());
            return false;
        }
        return true;
    }

    private static void removeOlderFiles(ChannelSftp sftpChannel, String remoteDir) {
        try {
            Vector vector = sftpChannel.ls(remoteDir);
            for (Object obj : vector) {
                if (obj instanceof com.jcraft.jsch.ChannelSftp.LsEntry) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) obj;
                    String fileName = entry.getFilename();
                    if (!fileName.endsWith(".html")) {
                        continue;
                    }
                    logger.info("File name : {}", fileName);
                    try {
                        SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
                        int sInd = fileName.indexOf("]-") + 2;
                        int eInd = fileName.indexOf(".html");

                        String dateString = fileName.substring(sInd, eInd);
                        Date fileCreatedDate = inputDateFormat.parse(dateString);
                        Date currentDate = new Date();

                        float diffInMillis = currentDate.getTime() - fileCreatedDate.getTime();
                        float days = diffInMillis / (24L * 60L * 60L * 1000L);
                        final float daysLimit = 7.0f;

                        if (days >= daysLimit) {
                            sftpChannel.rm(remoteDir + "/" + fileName);
                            logger.info("File removed - was created on : {}, How old : {} days", inputDateFormat.format(fileCreatedDate), String.format("%.2f",days));
                        } else {
                            logger.info("File not removed - was created on : {}, How old : {} days", inputDateFormat.format(fileCreatedDate), String.format("%.2f",days));
                        }
                    } catch (Exception e) {
                        logger.warn("Remove file error : {}", e.getMessage());
                    }
                }
            }
        } catch (Exception ex){
            logger.warn("Error occurred in : removeOlderFiles() -- {}", ex.getMessage());
        }
    }


    public static void copyFiles(final CliShell shell, final String command) {
        logger.info("Executing command: {}", command);
        try {
            final CliResult result = shell.execute(command);
            if (!result.isSuccess()) {
                logger.warn("Failed to execute command: {}", command);
                logger.warn("Output: {}", result.getOutput());
            }
        } catch (final Exception e) {
            logger.warn("Failed to execute command: {}", command);
            logger.warn("Message: {}", e.getMessage());
        }
    }

    public static void attachLogFilesToReport() {
        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final String scriptLogFile = "HighAvailability_"+ LocalDateTime.now().format(dateTimeFormatter)+".log";
        final String catLogFile = "catalina."+ LocalDateTime.now().format(dateTimeFormatter) +".log";
        final String upgradeStatusFile = "upgrade_status.log";
        final String uri = "watcher/adu/endpoints/";
        try {
            LocalDateTime.now().format(dateTimeFormatter);
            HttpResponse httpResponse = CloudNativeDependencyDowntimeHelper.generateHttpGetResponse(uri + scriptLogFile);
            if (httpResponse != null && HttpStatus.OK.equals(httpResponse.getResponseCode())) {
                writeFileToSlave(scriptLogFile, httpResponse.getContent());
                try {
                    txtFiles.put(scriptLogFile, IOUtils.toString(httpResponse.getContent(), StandardCharsets.UTF_8.name()));
                } catch (final Exception e) {
                    CommonUtils.isFileCopyFailed=true;
                }
            }
            HttpResponse catHttpResponse = CloudNativeDependencyDowntimeHelper.generateHttpGetResponse(uri + catLogFile);
            if (catHttpResponse != null && HttpStatus.OK.equals(catHttpResponse.getResponseCode())) {
                writeFileToSlave(catLogFile, catHttpResponse.getContent());
                try {
                    txtFiles.put(catLogFile, IOUtils.toString(catHttpResponse.getContent(), StandardCharsets.UTF_8.name()));
                } catch (final Exception e){
                    CommonUtils.isFileCopyFailed=true;
                }
            }
            HttpResponse ugHttpResponse = CloudNativeDependencyDowntimeHelper.generateHttpGetResponse(uri + upgradeStatusFile);
            if (ugHttpResponse != null && HttpStatus.OK.equals(ugHttpResponse.getResponseCode())) {
                writeFileToSlave(upgradeStatusFile, ugHttpResponse.getContent());
                try {
                    txtFiles.put(upgradeStatusFile, IOUtils.toString(ugHttpResponse.getContent(), StandardCharsets.UTF_8.name()));
                } catch (final Exception e){
                    CommonUtils.isFileCopyFailed=true;
                }
            }
        } catch (final Exception e){
            logger.warn("exception while attaching log files to report {} ",e.getMessage());
            CommonUtils.isFileCopyFailed=true;
        }
    }

    public static boolean isIngressWorking() {
        boolean isSuccess = false;
        try {
                HttpRestServiceClient httpRestServiceClient = HAPropertiesReader.getHttpRestServiceClientCloudNative();
                final List<String[]> bodies = new ArrayList<>();
                bodies.add(new String[]{"IDToken1", HAPropertiesReader.getEnmUsername()});
                bodies.add(new String[]{"IDToken2", HAPropertiesReader.getEnmPassword()});

                final HttpResponse httpResponse1 = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, bodies, null, "/login");
                logger.info("Login responseCode : {}", httpResponse1.getResponseCode().getCode());
                if (httpResponse1.getResponseCode().getCode() == 200) {
                    isSuccess = true;
                }

                final HttpResponse httpResponse2 = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, bodies, null, "/logout");
                logger.info("Logout responseCode : {}", httpResponse2.getResponseCode().getCode());
                if (httpResponse2.getResponseCode().getCode() == 200) {
                    isSuccess = true;
                }
            } catch (final Exception e) {
                logger.info("Exception in First Req {} :", e.getMessage());
            }
            return isSuccess;
        }

}
