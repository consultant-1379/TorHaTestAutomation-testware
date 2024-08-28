package com.ericsson.nms.rv.core.upgrade;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.cifwk.taf.data.Ports;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.EnmSystemException;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.sut.test.cases.HaltHost;
import com.ericsson.sut.test.cases.HardResetHost;
import com.ericsson.sut.test.cases.util.Neo4jPhysicalCloudDtCalculator;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import static com.ericsson.nms.rv.core.util.CommonUtils.sleep;

public class CommonDependencyDowntimeHelper extends UpgradeVerifier {
    private static final Logger logger = LogManager.getLogger(CommonDependencyDowntimeHelper.class);
    private static final String YYYY_MMDD_HHMMSS = "yyyyMMddHHmmss";
    public static final Map<String, String> EmptyDependencyMap = new HashMap<>();
    public static int ftsInstancesCount;


    public static List<String> neoDtCalculation(final String rw) throws EnmSystemException {
        List<String> temDTList = new ArrayList<>();
        long startTime =  System.currentTimeMillis();
        Neo4jCopyFilesThread neoFiles = new Neo4jCopyFilesThread();
        try {
            logger.info("copying neo4j files ..." );
            neoFiles.execute();
            do {
                sleep(1);
                logger.info("waiting for copying neo4j files ..." );
            } while (!neoFiles.isCompleted() && neoFiles.threadState() &&  !( System.currentTimeMillis()-startTime  > 600L * (long) Constants.TEN_EXP_3));
        } catch (final Exception e) {
            logger.info("Exception occurred while coping files {}", e.getMessage());
            try {
                if (neoFiles.threadState()) {
                    neoFiles.interruptThread();
                }
            } catch(final Exception ee) {
                logger.info("Exception occurred while destroying thread {}", ee.getMessage());
            }
            CommonUtils.isFileCopyFailed = true;
        }
        logger.info("time taken to copy files(seconds) {}", TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()-startTime ));
        if ("R".equalsIgnoreCase(rw.trim())) {
            return Neo4jPhysicalCloudDtCalculator.getNeo4jFollowerDt();
        } else if ("W".equalsIgnoreCase(rw.trim())) {
            return Neo4jPhysicalCloudDtCalculator.getNeo4jLeaderDt();
        }
        return temDTList;
    }

    static List<String> getFtsDownTimePeriods() {
        List<String> dTList = new ArrayList<>();
        final String filter = String.format(" | /bin/awk '$0 >=\"%d\" && $0<=\"%d\"'", upgradeStartTime, upgradeFinishTime);
        final String offDateCmd = HAPropertiesReader.ftsOffDateCmd;
        final String onDateCmd = "sudo /bin/egrep -h 'post.start.scripts.end' /var/log/messages | /bin/cut -b -15 | tail -n 1";

        if (HAPropertiesReader.isEnvCloudNative()) {
            //DO nothing.
        } else if (HAPropertiesReader.isEnvCloud()) {
            String getHost = "consul members | grep filetransferservice | /bin/awk -F ' ' '{print $1}'";
            final CliShell MS_SHELL = new CliShell(HAPropertiesReader.getMS());
            try {
                CliResult result = MS_SHELL.execute(getHost);
                String[] ftsHostNames = result.getOutput().split("\n");
                ftsInstancesCount = ftsHostNames.length;
                logger.info("ftsInstancesCount : {}", ftsInstancesCount);
                for (String ftsHostName : ftsHostNames) {
                    final CliShell ftsShell = new CliShell(getVmHost(ftsHostName));
                    logger.info("Executing command : {}", offDateCmd);
                    final CliResult offDateResult = ftsShell.execute(offDateCmd);
                    logger.info("Executing command : {}", onDateCmd);
                    final CliResult onDateResult = ftsShell.execute(onDateCmd);
                    logger.info("{} offDateCmd Result : {}", ftsHostName, offDateResult.getOutput());
                    logger.info("{} onDateCmd Result : {}", ftsHostName, onDateResult.getOutput());

                    if (!(offDateResult.getOutput().trim().isEmpty() || onDateResult.getOutput().trim().isEmpty())) {
                        final String offCmd = "date --date=\"" + offDateResult.getOutput() +"\" +%Y%m%d%H%M%S" + filter;
                        final String onCmd = "date --date=\"" + onDateResult.getOutput() +"\" +%Y%m%d%H%M%S" + filter;
                        logger.info("Executing off command : {}", offCmd);
                        final CliResult offResult = ftsShell.execute(offCmd);
                        logger.info("Executing on command : {}", onCmd);
                        final CliResult onResult = ftsShell.execute(onCmd);
                        logger.info("{} offCmd Result : {}", ftsHostName, offResult.getOutput());
                        logger.info("{} onCmd Result : {}", ftsHostName, onResult.getOutput());

                        if (!(offResult.getOutput().contains("invalid date") || onResult.getOutput().contains("invalid date"))) {
                            final String offDate = offResult.getOutput().trim();
                            final String onDate = onResult.getOutput().trim();
                            if (!(offDate.isEmpty() || onDate.isEmpty())) {
                                dTList.add(offDate);
                                dTList.add(onDate);
                            } else {
                                logger.warn("Matching off/on strings are not found for file-transfer service.");
                            }
                        }
                    } else {
                        logger.warn("off/on strings are not found for file-transfer service.");
                    }
                }
            } catch (final Exception e) {
                logger.error("FTS Message :  {}", e.getMessage());
            }
        } else {
            final List<Host> ftsHosts = getAllFTSHosts("filetransferservice");
            ftsInstancesCount = ftsHosts.size();
            logger.info("ftsInstancesCount : {}", ftsInstancesCount);
            try {
                for (final Host ftsHost : ftsHosts) {
                    ftsHost.setUser("cloud-user");
                    logger.info("ftsHost : {}", ftsHost.getHostname());
                    final CliShell ftsShell = new CliShell(ftsHost);
                    logger.info("Executing command : {}", offDateCmd);
                    final CliResult offDateResult = ftsShell.execute(offDateCmd);
                    logger.info("Executing command : {}", onDateCmd);
                    final CliResult onDateResult = ftsShell.execute(onDateCmd);
                    logger.info("{} offDateCmd Result : {}", ftsHost, offDateResult.getOutput());
                    logger.info("{} onDateCmd Result : {}", ftsHost, onDateResult.getOutput());

                    if (!(offDateResult.getOutput().trim().isEmpty() || onDateResult.getOutput().trim().isEmpty())) {
                        final String offCmd = "date --date=\"" + offDateResult.getOutput() +"\" +%Y%m%d%H%M%S" + filter;
                        final String onCmd = "date --date=\"" + onDateResult.getOutput() +"\" +%Y%m%d%H%M%S" + filter;
                        logger.info("Executing off command : {}", offCmd);
                        final CliResult offResult = ftsShell.execute(offCmd);
                        logger.info("Executing on command : {}", onCmd);
                        final CliResult onResult = ftsShell.execute(onCmd);
                        logger.info("{} offCmd Result : {}", ftsHost, offResult.getOutput());
                        logger.info("{} onCmd Result : {}", ftsHost, onResult.getOutput());

                        final String offDate;
                        final String onDate = onResult.getOutput().trim();
                        if (HAPropertiesReader.getTestSuitName().contains("HardResetHost")) {
                            offDate =  HardResetHost.hardResetTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                        } else if (HAPropertiesReader.getTestSuitName().contains("HaltHost")) {
                            offDate = HaltHost.haltHostTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                        } else {
                            offDate = offResult.getOutput().trim();
                        }
                        if (!(offResult.getOutput().contains("invalid date") || onResult.getOutput().contains("invalid date"))) {
                            if (!(offDate.isEmpty() || onDate.isEmpty())) {
                                dTList.add(offDate + "-off");
                                dTList.add(onDate + "-on");
                            } else {
                                logger.warn("Matching off/on strings are not found for file-transfer service.");
                            }
                        }
                    } else {
                        logger.warn("off/on strings are not found for file-transfer service.");
                    }
                }
            } catch (final Exception e) {
                logger.error("FTS Message :  {}", e.getMessage());
            }
        }
        logger.info("getFtsDownTimePeriods dTList : {}", dTList);
        return dTList;
    }

    private static List<Host> getAllFTSHosts(String name) {
        final String ftsCommand = String.format("cat /etc/hosts | grep %s | awk -F ' ' '{print $1}'", name);
        CliShell msShell = new CliShell(HAPropertiesReader.getMS());
        CliResult ftsResult = msShell.execute(ftsCommand);
        logger.info("ftsResult : {}", ftsResult.getOutput());
        String[] ftsHostsIpList = ftsResult.getOutput().split("\n");
        List<Host> ftsHosts = new ArrayList<>();
        for(String ftsHostName : ftsHostsIpList) {
            ftsHosts.add(getVmHost(ftsHostName));
        }
        logger.info("ftsHosts : {}", ftsHosts);
        return ftsHosts;
    }

    private static Host getVmHost(final String hostName) {
        logger.info("ftsHostName : {}", hostName);
        Host ftsHost = new Host();
        ftsHost.setHostname(hostName);
        ftsHost.setIp(hostName);
        ftsHost.setUser("cloud-user");
        ftsHost.setType(HostType.JBOSS);
        Map<Ports, String> port = new HashMap<>();
        port.put(Ports.SSH, "22");
        ftsHost.setPort(port);
        return ftsHost;
    }

    public static List<String> buildDowntimeList(final String[] strings) {
        return Stream.of(strings).filter(s -> s.matches("\\d+")
                && Long.parseLong(s) > upgradeStartTime
                && Long.parseLong(s) < upgradeFinishTime)
                .collect(Collectors.toList());
    }

    public static StringBuilder getStringBuilder(final List<String> downTimePeriods) {
        final StringBuilder builder = new StringBuilder();
        if (downTimePeriods.size() != 1) {
            if (downTimePeriods.size() % 2 == 0) {
                for (int j = 0; j < downTimePeriods.size(); j += 2) {
                    builder.append(getDtTimes(downTimePeriods.get(j), downTimePeriods.get(j + 1)));
                }
            } else {
                for (int i = 0; i < downTimePeriods.size() - 1; i += 2) {
                    builder.append(getDtTimes(downTimePeriods.get(i), downTimePeriods.get(i + 1)));
                }
            }
        }
        logger.info("DT StringBuilder : {}", builder);
        return builder;
    }

    public static Map<String, String> addComponentToMap(String component, List<String> downTimePeriodsLt) {
        final StringBuilder builder = new StringBuilder();
        Map<String, String> dependencyMap = new HashMap<>();
        if (!downTimePeriodsLt.isEmpty() && downTimePeriodsLt.size() > 1 && downTimePeriodsLt.size() % 2 == 0) {
            dependencyMap.put(component, getStringBuilder(downTimePeriodsLt).toString());
        } else if (downTimePeriodsLt.isEmpty()) {
            EmptyDependencyMap.put(component, (builder.append("N/A").append(",").append("N/A").append(",").append("0s").append(";")).toString());
        } else if (downTimePeriodsLt.size() > 1 && downTimePeriodsLt.size() % 2 != 0) {
            dependencyMap.put(component, getStringBuilder(downTimePeriodsLt).toString());
            EmptyDependencyMap.put(component, getOnlyFromTime(downTimePeriodsLt).toString());
        } else {
            EmptyDependencyMap.put(component, getOnlyFromTime(downTimePeriodsLt).toString());
        }
        logger.warn("dependencyMap in addComponentToMap:{} {}", dependencyMap.get(component), component);
        logger.warn("EmptyDependencyMap content in addComponentToMap:{} {}", EmptyDependencyMap.get(component), component);
        return dependencyMap;
    }

    private static StringBuilder getOnlyFromTime(List<String> downTimePeriods) {
        final DateFormat sdf = new SimpleDateFormat(YYYY_MMDD_HHMMSS);
        final DateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final StringBuilder builder = new StringBuilder();
        try {
            final Date dateTimeInitOffline = sdf.parse(String.valueOf(downTimePeriods.get(downTimePeriods.size() - 1)));
            builder.append(sdf1.format(dateTimeInitOffline)).append(",").append("N/A").append(",").append("N/A").append(";");
        } catch (final ParseException e) {
            logger.warn(e.getMessage(), e);
        }
        return builder;
    }

    private static StringBuilder getDtTimes(String fromTime, String toTime) {
        final DateFormat sdf = new SimpleDateFormat(YYYY_MMDD_HHMMSS);
        final DateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final StringBuilder internalBuilder = new StringBuilder();
        try {
            final Date dateTimeInitOffline = sdf.parse(fromTime);
            final Date dateTimeFinishOnline = sdf.parse(toTime);
            long diff = dateTimeFinishOnline.getTime() - dateTimeInitOffline.getTime();
            String diffString = String.format("%02d", (diff / 1000));
            logger.info("diffString : {}", diffString);
            internalBuilder.append(sdf1.format(dateTimeInitOffline)).append(",").append(sdf1.format(dateTimeFinishOnline)).append(",").append(diffString).append(";");
        } catch (final ParseException e) {
            logger.warn(e.getMessage(), e);
        }
        return internalBuilder;
    }
}
