package com.ericsson.nms.rv.core.upgrade;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ericsson.cifwk.taf.configuration.TafConfigurationProvider;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CertUtil;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.ericsson.nms.rv.core.EnmSystemException;

public class CloudDependencyDowntimeHelper extends UpgradeVerifier {
    private static final Logger logger = LogManager.getLogger(CloudDependencyDowntimeHelper.class);
    private static int timeCounter = 0;
    private static int statusFlag = 0;
    private static int timeFlag = 0;
    private static int tmpFlag = 0;

    CloudDependencyDowntimeHelper() {}

    Map<String, String> buildKnownDowntimeMapForCloud() {
        final Map<String, String> downtimeMap = new HashMap<>();
        final List<String> otherDependencies = new ArrayList<>();
        final String copyCommand = "cp -rf "+HAPropertiesReader.path+"/aduTimes /var/tmp/";
        otherDependencies.add(HAPropertiesReader.POSTGRES);
        otherDependencies.add(HAPropertiesReader.JMS);
        otherDependencies.add(HAPropertiesReader.ESHISTORY);
        otherDependencies.add(HAPropertiesReader.VISINAMINGNB);
        otherDependencies.add(HAPropertiesReader.HAPROXY);
        otherDependencies.add(HAPropertiesReader.ELASTICSEARCH);
        otherDependencies.add(HAPropertiesReader.OPENIDM);
        otherDependencies.add(HAPropertiesReader.NFSPMLINKS);
        otherDependencies.add(HAPropertiesReader.NFSSMRS);
        otherDependencies.add(HAPropertiesReader.NFSDATAD);
        otherDependencies.add(HAPropertiesReader.NFSPM1);
        otherDependencies.add(HAPropertiesReader.NFSPM2);
        otherDependencies.add(HAPropertiesReader.NFSMDT);
        otherDependencies.add(HAPropertiesReader.MODELS);
        otherDependencies.add(HAPropertiesReader.NFSBATCH);
        otherDependencies.add(HAPropertiesReader.NFSCONFIGMGT);
        otherDependencies.add(HAPropertiesReader.NFSDDCDATA);
        otherDependencies.add(HAPropertiesReader.NFSCUSTOM);
        otherDependencies.add(HAPropertiesReader.NFSAMOS);
        otherDependencies.add(HAPropertiesReader.NFSHCDUMPS);
        otherDependencies.add(HAPropertiesReader.NFSHOME);
        otherDependencies.add(HAPropertiesReader.NFSNOROLLBACK);
        CommonUtils.copyFiles(SHELL_MS, copyCommand);
        otherDependencies.forEach(component -> {
            List<String> downTimePeriods;
            int counter = 0;
            do {
                try {
                    downTimePeriods = getDownTimePeriodsFromWatcherFiles(component, component.concat("Times.txt"), SHELL_MS);
                    downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(component, downTimePeriods));
                } catch (final EnmSystemException e) {
                    logger.info("command failed to execute {}", component);
                    downTimePeriods = new ArrayList<>();
                }
            } while (downTimePeriods.isEmpty() && (++counter) < 3);
        });

        List<String> downTimePeriods;
        final String listNeo4j = HostConfigurator.getAllHosts(HAPropertiesReader.NEO4J).stream().map(Host::getHostname).collect(Collectors.joining(","));
        System.setProperty("taf.config.dit.deployment.internal.nodes", listNeo4j);
        TafConfigurationProvider.provide().reload();
        if (generatingPemKey()) {
            logger.info("Cloud NEO4J Leader Calculation Started");
            try {
                downTimePeriods = CommonDependencyDowntimeHelper.neoDtCalculation("W");
                downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(NEO4J_LEADER, downTimePeriods));
            } catch (final EnmSystemException e) {
                logger.info("command failed to execute {}", "NEO4J Leader");
                downTimePeriods = new ArrayList<>();
            }
            logger.info("Cloud NEO4J - Leader downTimePeriods{}", downTimePeriods);
            List<String> downTimePeriodsR;
            logger.info("Cloud NEO4J Follower Calculation Started");
            try {
                downTimePeriodsR = CommonDependencyDowntimeHelper.neoDtCalculation("R");
                downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(NEO4J_FOLLOWER, downTimePeriodsR));
            } catch (final EnmSystemException e) {
                logger.info("command failed to execute {}", "NEO4J Follower");
                downTimePeriodsR = new ArrayList<>();
            }
            logger.info("Cloud NEO4J - Follower downTimePeriods{}", downTimePeriodsR);
        }
        downtimeMap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(HAPropertiesReader.FTS, CommonDependencyDowntimeHelper.getFtsDownTimePeriods()));
        logger.info("buildKnownDowntimeMapForCloud --- downtimeMap: {}", downtimeMap.toString());
        return downtimeMap;
    }

    private static List<String> getDownTimePeriodsFromWatcherFiles(final String app, final String aduTimesFile, final CliShell shell) throws EnmSystemException {
        final CliResult execResult;
        final String catCommand = "cat "+HAPropertiesReader.path+"/aduTimes/".concat(aduTimesFile);
        logger.info("cat command with adu file is={}", catCommand);
        List<String> resultTimeValues = new ArrayList<>();
        try {
            execResult = shell.execute(catCommand);
            if (execResult.isSuccess()) {
                final String[] commandOutput = execResult.getOutput().split("\\n");
                InputStream inputStream = new ByteArrayInputStream(execResult.getOutput().getBytes(StandardCharsets.UTF_8));
                try {
                    CommonUtils.txtFiles.put(aduTimesFile, IOUtils.toString(inputStream, StandardCharsets.UTF_8.name()));
                } catch (final IOException e) {
                    logger.info("{} file copy failed {}", aduTimesFile, e.getMessage());
                    CommonUtils.isFileCopyFailed = true;
                }
                timeCounter = 0;
                statusFlag = 0;
                timeFlag = 0;
                for (String modifiedCommandOutput : commandOutput) {
                    processCommandOutput(modifiedCommandOutput.trim(), resultTimeValues);
                }
                logger.info("List resultTimeValues: {}", resultTimeValues.toString());

                if (!resultTimeValues.isEmpty()) {
                    final String[] dtArr = resultTimeValues.toArray(new String[resultTimeValues.size()]);
                    final List<String> dtPeriodsList = buildDowntimeListWatcher(dtArr);
                    logger.debug("getDownTimePeriods for {} is {}", app, dtPeriodsList);
                    return dtPeriodsList;
                } else {
                    logger.warn("getDownTimePeriods for {} is EMPTY", app);
                    return new ArrayList<>();
                }
            } else {
                logger.warn("Failed to get DT for {}", app);
                throw new EnmSystemException("command Failed to execute");
            }
        }
        catch (final Exception e){
            logger.warn("In catch Failed to get DT for {} : {}", app,e.getMessage());
            CommonUtils.isFileCopyFailed = true;
            throw new EnmSystemException("command Failed to execute" + e.getMessage());
        }
    }

    private static List<String> buildDowntimeListWatcher(final String[] dtStrings) {
        return Stream.of(dtStrings).filter(s -> Long.parseLong(s) > upgradeStartTime
                && Long.parseLong(s) < upgradeFinishTime)
                .collect(Collectors.toList());
    }

    private static void processCommandOutput(final String commandOutputStr, List<String> lstLines) {
        if (commandOutputStr.contains("Time")) {
            timeCounter++;
            if (timeCounter == 2) {
                String[] str = commandOutputStr.split(" ");
                logger.info("DownTimer Started at: {}", str[1]);
                lstLines.add(str[1]);
                timeFlag = 1;
                tmpFlag++;
                if(tmpFlag==2){
                    lstLines.remove(str[1]);
                    tmpFlag=0;
                }
            }
            if (statusFlag == 3 && timeFlag == 1) {
                String[] str = commandOutputStr.split(" ");
                logger.info("DownTimer Stopped at:  {}", str[1]);
                lstLines.add(str[1]);
                timeFlag = 0;
                timeCounter = 0;
                tmpFlag=0;
            }
            statusFlag = 0;
        } else if (commandOutputStr.contains("Status")){
            timeCounter = 0;
            if(commandOutputStr.contains("passing"))
            {
                statusFlag++;
            }
        }
    }

    public static boolean generatingPemKey() {
        boolean isCertInstalled = false;
        final String cert = CertUtil.getCertificate();
        if (!cert.isEmpty()) {
            final String pemFileCmd = String.format("/usr/bin/rm -rf /var/tmp/Bravo/;" + "/usr/bin/mkdir /var/tmp/Bravo/;" + "/usr/bin/echo " + "\"%s\" > /var/tmp/Bravo/pemKey.pem;chmod 600 /var/tmp/Bravo/pemKey.pem;sed -i '/^$/d' /var/tmp/Bravo/pemKey.pem", cert);
            logger.info("Pem command is {}", pemFileCmd);

            if (SHELL_MS.execute(pemFileCmd).isSuccess()) {
                logger.info("Pem command  executed successfully ");
                isCertInstalled = true;
            } else {
                logger.info("Problem in executing Pem command  ");
            }
        }
        logger.info("isCertInstalled : {}", isCertInstalled);
        return isCertInstalled;
    }

}
