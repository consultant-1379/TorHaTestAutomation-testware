package com.ericsson.nms.rv.core;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.TreeMap;

public class ClusterReport {

    private static final Logger logger = LogManager.getLogger(ClusterReport.class);
    public static final Map<String, String> hostMap = new TreeMap<>();
    public static final Map<String, String> dataMap = new TreeMap<>();
    private static final String command = "/opt/ericsson/enminst/bin/vcs.bsh --groups | grep %s";


    public static void buildClusterReport() {
        CliShell shell = new CliShell(HAPropertiesReader.getMS());
        final String command = "cat /etc/hosts | /bin/awk '{print $2}' | grep 'db-'";
        final CliResult result = shell.executeAsRoot(command);
        final String resultString = result.getOutput();

        logger.info("resultString : {}", resultString);
        for (String db : resultString.split("\n")) {
            logger.info("db : {}", db);
            switch (db) {
                case "db-1":
                    hostMap.put("db-1", getHostName(HostConfigurator.getDb1()));
                    break;
                case "db-2":
                    hostMap.put("db-2", getHostName(HostConfigurator.getDb2()));
                    break;
                case "db-3":
                    hostMap.put("db-3", getHostName(HostConfigurator.getDb3()));
                    break;
                case "db-4":
                    hostMap.put("db-4", getHostName(HostConfigurator.getDb4()));
                    break;
                default:
                    logger.warn("Invalid db name : {}", db);
            }
        }

        hostMap.put("svc-1", getHostName(HostConfigurator.getSVC1()));
        hostMap.put("svc-2", getHostName(HostConfigurator.getSVC2()));
        hostMap.put("svc-3", getHostName(HostConfigurator.getSVC3()));
        hostMap.put("svc-4", getHostName(HostConfigurator.getSVC4()));
        logger.info("svc/db hostMap : {}", hostMap);
        buildClusterData();
    }

    public static void buildClusterData() {
        final CliShell MS_SHELL = new CliShell(HostConfigurator.getMS());
        try {
            hostMap.forEach((key, hostname) -> {
                final String cmd = String.format(command, hostname);
                logger.info("cmd : {}", cmd);
                if (!hostname.isEmpty()) {
                    final CliResult result = MS_SHELL.executeAsRoot(cmd);
                    dataMap.put(key, result.getOutput());
                }
            });
        } catch (Exception e) {
            logger.warn("Exception in buildClusterData.");
        }
    }

    private static String getHostName(final Host host) {
        final CliShell shell = new CliShell(host);
        try {
            final CliResult result = shell.execute("hostname");
            return result.getOutput();
        } catch (final Exception e) {
            logger.warn("Exception in getHostName.");
        }
        return "";
    }

}
