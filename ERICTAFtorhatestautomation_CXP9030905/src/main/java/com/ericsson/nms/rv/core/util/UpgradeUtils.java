package com.ericsson.nms.rv.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

import com.ericsson.cifwk.taf.configuration.TafConfigurationProvider;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.ericsson.nms.rv.core.HAPropertiesReader.PRODUCT_NAME;
import static com.ericsson.nms.rv.core.HAPropertiesReader.path;
import static com.ericsson.nms.rv.core.HAPropertiesReader.neo4jVersion;


public class UpgradeUtils {
    private static final CliShell SHELL = new CliShell(HAPropertiesReader.getMS());
    private static final Logger logger = LogManager.getLogger(UpgradeUtils.class);
    private static final String  deleteJarFileCmd = "rm -rf "+PRODUCT_NAME+"*";
    public static String physicalUpgradeType = "RH7";

    public static void initWatcherScript() throws IOException {
        if (HAPropertiesReader.isEnvCloudNative()) {
            if (HAPropertiesReader.getTestType().equalsIgnoreCase("Regression")) {
                startStopCloudNativeWatcherScript("endpoint", "start");
            }
            //In Upgrade script will auto start.
        } else if (HAPropertiesReader.isEnvCloud()) {
            logger.info("-------------------------- Stopping Bravo Service Watcher Script ---------------------------");
            final String SCRIPT_STOP_RESULT = SHELL.execute("(/usr/bin/ps -ef | /usr/bin/grep \"consul watch\" | /usr/bin/grep \""+path+"/data/date.sh\" | /usr/bin/awk '{print $2}')").getOutput();
            logger.info("SCRIPT_STOP_RESULT : {}", SCRIPT_STOP_RESULT);
            if (!SCRIPT_STOP_RESULT.isEmpty()) {
                final String[] strings = SCRIPT_STOP_RESULT.split("\n");
                final StringBuilder commandBuilder = new StringBuilder();
                for (final String str : strings) {
                    commandBuilder.append("kill -9 ").append(str).append(";");
                }
                final String command = commandBuilder.toString();
                final String stopCommand = command.substring(0, command.length() -1);
                logger.info("Stop Script command : {}", stopCommand);
                final String SCRIPT_KILL_STOP_RESULT = SHELL.execute(stopCommand).getOutput();
                if (SCRIPT_KILL_STOP_RESULT.isEmpty()) {
                    logger.info("Sucessfully executed the StopBravoServiceWatcherScript {}", SCRIPT_STOP_RESULT);
                } else {
                    logger.info("Failed to execute the StopBravoServiceWatcherScript{}", SCRIPT_STOP_RESULT);
                }
            }
            removeNeoData("cloud", false);
            extractWatcherScripts();
            logger.info("--------------- Starting Bravo Service Watcher Bash Script -----------------------");
            final String commandStart = "/bin/sed -i -e 's/\\r$//' "+path+"/data/date.sh; /bin/sed -i -e 's/\\r$//' "+path+"/data/startBravoServiceWatchers.sh ; chmod +x "+path+"/data/*.sh ; "+path+"/data/startBravoServiceWatchers.sh "+path+"";
            final String neo4Jchmodcmd = "/bin/sed -i -e 's/\\r$//' "+path+"/data/Neo4j-DT/*.sh; chmod 777 "+path+"/data/Neo4j-DT/*.sh; mkdir "+path+"/Neo4j-DT/";
            final String SCRIPT_START_RESULT2 = SHELL.execute(commandStart).getOutput();
            logger.info(SCRIPT_START_RESULT2);
            final String SCRIPT_START_RESULT3 = SHELL.execute(neo4Jchmodcmd).getOutput();
            logger.info(SCRIPT_START_RESULT3);
            final String listNeo4j = HostConfigurator.getAllHosts(HAPropertiesReader.NEO4J).stream().map(Host::getHostname).collect(Collectors.joining(","));
            System.setProperty("taf.config.dit.deployment.internal.nodes", listNeo4j);
            TafConfigurationProvider.provide().reload();
            final String neoHostCmd = "consul members | grep \"neo4j\" |awk {'print $1}'";
            String result = "";
            int cmdCounter = 0;
            while(cmdCounter < 2) {
                result = SHELL.execute(neoHostCmd).getOutput();
                if (!result.isEmpty()){
                    logger.info("Neo4j HostName details are {}", result);
                    break;
                } else {
                    cmdCounter = cmdCounter + 1;
                    continue;
                }
            }
            if (!result.isEmpty()) {
                final String[] neoHostName  = result.split("\n");
                int counter = 0;
                for (final String neo4j : neoHostName) {
                    final StringBuilder neo4JWriteshellCommand = new StringBuilder();
                    final StringBuilder neo4JReadshellCommand = new StringBuilder();
                    neo4JWriteshellCommand.append("nohup "+path+"/data/Neo4j-DT/StartNeo4j.sh ").append(neo4j).append(" "+path+"/Neo4j-DT/Eng-Neo4jWriteOut-").append(counter).append(".csv").append(" cloud").append(" W").append(" " +neo4jVersion).append(" "+path);
                    logger.info("Cloud Neo4J shellCommand is {}", neo4JWriteshellCommand);
                    String neo4JLeaderCmdResult = SHELL.execute(neo4JWriteshellCommand.toString()).getOutput();
                    logger.info("Cloud neo4JLeaderCmdResult {}", neo4JLeaderCmdResult);
                    neo4JReadshellCommand.append("nohup "+path+"/data/Neo4j-DT/StartNeo4j.sh ").append(neo4j).append(" "+path+"/Neo4j-DT/Eng-Neo4jReadOut-").append(counter).append(".csv").append(" cloud").append(" R").append(" " +neo4jVersion).append(" "+path);
                    logger.info("Cloud Neo4J Read shellCommand is {}", neo4JReadshellCommand);
                    String neo4JFollowerCmdResult = SHELL.execute(neo4JReadshellCommand.toString()).getOutput();
                    logger.info("Cloud neo4JFollowerCmdResult {}", neo4JFollowerCmdResult);
                    counter = counter + 1;
                }
            } else {
                logger.warn("Unable to get neo4jHostName details..");
            }
        }
    }

    /**
     * cENM comment.
     * Start/stop Upgrade Status script in case of cENM UG.
     * Start/stop Dependency endpoint watcher scripts in case of UG/Regression.
     * @param script - upgrade/endpoint
     * @param action - start/stop
     */
    public static void startStopCloudNativeWatcherScript(final String script, final String action) {
        try {
            logger.info("startStopCloudNativeWatcher -- {} script called .. action: {}", script, action);
            HttpRestServiceClient httpRestServiceClient = HAPropertiesReader.getHttpRestServiceExternalClient();
            final String uri = script.equalsIgnoreCase("upgrade") ? "watcher/adu/script/upgrade/" + action : "watcher/adu/script/" + action;
            final HttpResponse httpResponse1 = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
            logger.info("Watcher script {} action {} responseCode : {}", script, action, httpResponse1.getResponseCode().getCode());
            if (!HAPropertiesReader.isExternalVmUsed()) {
                boolean startSecondScript = false;
                int count = 0;
                CommonUtils.sleep(1);
                while (!startSecondScript && count < 10) {
                    final HttpResponse httpResponse2 = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
                    final Scanner scanner = new Scanner(httpResponse2.getContent());
                    String status = "";
                    if (scanner.hasNext()) {
                        status = scanner.next();
                    }
                    logger.info("startStopCloudNativeWatcher {} script {} status : {}", script, action, status);
                    startSecondScript = Boolean.parseBoolean(status);
                    count++;
                }
                logger.info("Second {} script {} successfully.", script, action);
            }
        } catch (final Exception e) {
            logger.warn("Error Message : {}", e.getMessage());
        }
    }

    public static void initPhysicalNeo4j() throws IOException {
        if (!HAPropertiesReader.getTestType().equalsIgnoreCase("Regression")) {
            if(HAPropertiesReader.neoconfig60KFlag) {
                removeNeoData("physical", false);
            }
            extractWatcherScripts();
            final String neo4Jchmodcmd = "/bin/sed -i -e 's/\\r$//' "+ path+"/data/Neo4j-DT/*.sh; sudo chmod 777 "+path+"/data/Neo4j-DT/*.sh; sudo mkdir "+path+"/Neo4j-DT/";
            final String SCRIPT_START_RESULT = SHELL.executeAsRoot(neo4Jchmodcmd).getOutput();
            logger.info("SCRIPT_START_RESULT : {}", SCRIPT_START_RESULT);
            final String listNeo4j = HostConfigurator.getAllHosts(HAPropertiesReader.NEO4J).stream().map(Host::getHostname).collect(Collectors.joining(","));
            System.setProperty("taf.config.dit.deployment.internal.nodes", listNeo4j);
            TafConfigurationProvider.provide().reload();
            final String neoHostCmd = "/opt/ericsson/enminst/bin/vcs.bsh --groups | grep \"sg_neo4j_clustered_service\" |awk {'print $3}'";
            int cmdCounter = 0;
            String result = "";
            while(cmdCounter < 2) {
                result = SHELL.executeAsRoot(neoHostCmd).getOutput();
                if (!result.isEmpty()){
                    logger.info("Neo4j HostName details are {}", result);
                    break;
                } else {
                    cmdCounter = cmdCounter + 1;
                    continue;
                }

            }
            if (!result.isEmpty()) {
                final String[] neoHostName = result.split("\n");
                int counter = 0;
                for (final String neo4j : neoHostName) {
                    final StringBuilder neo4JWshellCommand = new StringBuilder();
                    final StringBuilder neo4JFshellCommand = new StringBuilder();
                    neo4JWshellCommand.append("nohup "+path+"/data/Neo4j-DT/StartNeo4j.sh ").append(neo4j).append(" "+path+"/Neo4j-DT/Eng-Neo4jWriteOut-").append(counter).append(".csv").append(" Physical").append(" W").append(" " +neo4jVersion).append(" "+path);
                    logger.info("Physical Neo4JW shellCommand is {}", neo4JWshellCommand);
                    String neo4JLeaderCmdResult = SHELL.executeAsRoot(neo4JWshellCommand.toString()).getOutput();
                    logger.info("Physical neo4JLeaderCmdResult {}", neo4JLeaderCmdResult);
                    neo4JFshellCommand.append("nohup "+path+"/data/Neo4j-DT/StartNeo4j.sh ").append(neo4j).append(" "+path+"/Neo4j-DT/Eng-Neo4jReadOut-").append(counter).append(".csv").append(" Physical").append(" R").append(" " +neo4jVersion).append(" "+path);
                    logger.info("Physical Neo4JF shellCommand is {}", neo4JFshellCommand);
                    String neo4JFollowerCmdResult = SHELL.executeAsRoot(neo4JFshellCommand.toString()).getOutput();
                    logger.info("Physical neo4JFollowerCmdResult {}", neo4JFollowerCmdResult);
                    counter = counter + 1;
                }
            } else {
                logger.warn("Unable to get neo4jHostName details..");
            }
        }
    }

    private static void extractWatcherScripts() throws IOException {
        final String versionCommand;
        final String VERSION;
        final String snapshotJarNumber;
        final String jarFile;
        final String extract;
        final String unzipCommand;
        Properties props = new Properties();
        InputStream is = UpgradeUtils.class.getResourceAsStream("/project.properties");
        props.load(is);
        final String pkgVersion = props.getProperty("build.version");
        final String urlSnapshot = HAPropertiesReader.getNexusUrl() + "/content/repositories/snapshots/com/ericsson/nms/rv/taf/" + PRODUCT_NAME + "/";
        final String urlRelese = HAPropertiesReader.getNexusUrl() + "/content/groups/public/com/ericsson/nms/rv/taf/" + PRODUCT_NAME + "/";
        logger.info("urlSnapshot : {}", urlSnapshot);
        logger.info("urlRelease : {}", urlRelese);
        logger.info("pkgVersion = {}", pkgVersion);
        if (pkgVersion.endsWith("SNAPSHOT")) {
            final String jarFileDownload = "wget -nv " + urlSnapshot;
            final String snapshotJar = "echo `curl -s " + urlSnapshot + pkgVersion + "/maven-metadata.xml |  grep -i \"value\"|tail -1 | cut -d '>' -f2 | cut -d '<' -f1`";
            logger.info("snapshotJar command : {}", snapshotJar);
            snapshotJarNumber = SHELL.execute(snapshotJar).getOutput();
            jarFile = jarFileDownload.concat(pkgVersion + "/" + PRODUCT_NAME + "-" + snapshotJarNumber + ".jar");
            extract = "jar -xf " + PRODUCT_NAME + "-" + snapshotJarNumber + ".jar com/ericsson/sut/test/cases/util/ data/startBravoServiceWatchers.sh data/stopBravoServiceWatchers.sh data/date.sh data/Neo4j-DT/";
            unzipCommand = "unzip -o " + PRODUCT_NAME + "-" + snapshotJarNumber + ".jar com/ericsson/sut/test/cases/util/* data/startBravoServiceWatchers.sh data/stopBravoServiceWatchers.sh data/date.sh data/Neo4j-DT/*";
        } else {
            final String jarFileDownload = "wget -nv " + urlRelese;
            versionCommand = "echo `curl -s " + urlRelese + "maven-metadata.xml |  grep -i \"<version>\" | grep -v SNAPSHOT |tail -1 |  cut -d '>' -f2 | cut -d '<' -f1`";
            logger.info("versionCommand : {}", versionCommand);
            VERSION = SHELL.execute(versionCommand).getOutput();
            logger.info("VERSION : {}", VERSION);
            final String finalVersion;
            if (VERSION != null && !VERSION.isEmpty()) {
                finalVersion = VERSION;
            } else {
                logger.info("VERSION is empty .. using pkgVersion : {}", pkgVersion);
                finalVersion = pkgVersion;
            }
            logger.info("finalVersion : {}", finalVersion);
            jarFile = jarFileDownload.concat(finalVersion + "/" + PRODUCT_NAME + "-" + finalVersion + ".jar");
            extract = "jar -xf "+ PRODUCT_NAME +"-" + finalVersion + ".jar com/ericsson/sut/test/cases/util/ data/startBravoServiceWatchers.sh data/stopBravoServiceWatchers.sh data/date.sh data/Neo4j-DT/";
            unzipCommand = "unzip -o " + PRODUCT_NAME + "-" + finalVersion + ".jar com/ericsson/sut/test/cases/util/* data/startBravoServiceWatchers.sh data/stopBravoServiceWatchers.sh data/date.sh data/Neo4j-DT/*";
        }
        final String deleteJarCmd =SHELL.execute(deleteJarFileCmd).getOutput();
        logger.info("deleteJarCmd : {}, command : {}",deleteJarCmd, deleteJarFileCmd);
        final CliResult jarFileResult = SHELL.execute(jarFile);
        if (!jarFileResult.isSuccess()) {
            logger.warn("Failed to download jar file using command: \n{}", jarFile);
        }
        logger.info("jarFileResult : {}, command : {}", jarFileResult , jarFile);
        final CliResult jarExtractResult = SHELL.execute(extract);
        logger.info("jarExtractResult : {}, command : {}", jarExtractResult.getOutput(), extract );
        if (!jarExtractResult.isSuccess()) {
            logger.info("Failed to extract watcher files using jar command ..  retrying using unzip command : {}", unzipCommand);
            CommonUtils.sleep(1);
            final CliResult unzipResult = SHELL.execute(unzipCommand);
            if (unzipResult.isSuccess()) {
                logger.info("Successfully extracted watcher scripts: \n {}", unzipResult.getOutput());
            } else {
                logger.error("Failed to extract watcher scripts. : {}", unzipResult.getOutput());
            }
        }
    }

    public static void removeNeoData(final String env, final boolean preserveNeo4jData) {
        String cmdNEODt = "";
        String cmdNEOUg = "";
        final String commandLog = "Command: {} executed successfully.";
        if (env.equalsIgnoreCase("cloud")){
            cmdNEODt = "rm -rf "+path+"/Neo4j-DT";
            cmdNEOUg = "rm -rf "+path+"/UG";
        } else if(env.equalsIgnoreCase("physical")) {
            cmdNEODt = "rm -rf "+path+"/Neo4j-DT";
            cmdNEOUg = "rm -rf "+path+"/UG";
            final String deleteJarCmd =SHELL.executeAsRoot(deleteJarFileCmd).getOutput();
            logger.info("deleteJarCmd {} command {}",deleteJarCmd, deleteJarFileCmd);
        }
        CliResult rmCmd = envExecute(env,cmdNEOUg);
        if (rmCmd != null && rmCmd.isSuccess()) {
            logger.info("ug file deleted {}",rmCmd.toString());
            logger.info(commandLog, cmdNEOUg);
        } else {
            CliResult rmCmd1 = envExecute(env,cmdNEOUg);
            if (rmCmd1 != null &&rmCmd1.isSuccess()) {
                logger.info(commandLog, cmdNEOUg);
            }
        }
        if(!preserveNeo4jData) {
            CliResult rmCmdUg = envExecute(env,cmdNEODt);
            if (rmCmdUg != null && rmCmdUg.isSuccess()) {
                logger.info(commandLog, cmdNEODt);
            } else {
                CliResult rmCmdUg1 =envExecute(env,cmdNEODt);
                if (rmCmdUg != null && rmCmdUg1.isSuccess()) {
                    logger.info(commandLog, cmdNEODt);
                }
            }
        }
    }
    private static CliResult envExecute(final String env, final String cmd){
        CliResult result = null;
        if(env.equalsIgnoreCase("cloud")){
            result = SHELL.execute(cmd);
        }
        else if(env.equalsIgnoreCase("physical")){
            result = SHELL.executeAsRoot(cmd);
        }
        return result;
    }

    public static void stopBravoWatcherScript(final boolean bValue) {
        if (HAPropertiesReader.isEnvCloud() && bValue) {
            logger.info("-------------------------- Stopping Bravo Service Watcher Script ---------------------------");
            final String SCRIPT_STOP_RESULT = SHELL.execute("(/usr/bin/ps -ef | /usr/bin/grep \"consul watch\" | /usr/bin/grep \""+path+"/data/date.sh\" | /usr/bin/awk '{print $2}')").getOutput();
            logger.info("SCRIPT_STOP_RESULT : {}", SCRIPT_STOP_RESULT);
            if (!SCRIPT_STOP_RESULT.isEmpty()) {
                final String[] strings = SCRIPT_STOP_RESULT.split("\n");
                final StringBuilder commandBuilder = new StringBuilder();
                for (final String str : strings) {
                    commandBuilder.append("kill -9 ").append(str).append(";");
                }
                final String command = commandBuilder.toString();
                final String stopCommand = command.substring(0, command.length() -1);
                logger.info("Stop Script command : {}", stopCommand);
                final String SCRIPT_KILL_STOP_RESULT = SHELL.execute(stopCommand).getOutput();
                logger.info("Stop Script Kill command result : {}", SCRIPT_KILL_STOP_RESULT);
                if (SCRIPT_KILL_STOP_RESULT.isEmpty()) {
                    logger.info("Successfully executed the StopBravoServiceWatcherScript {}", SCRIPT_STOP_RESULT);
                } else {
                    logger.info("Failed to execute the Script{}", SCRIPT_STOP_RESULT);
                }
            }
        }
    }

    public static void extractEnminstPackage() {
        String litpVersion = "";
        final String ugTypeCommand = "/var/tmp/opt/ericsson/enminst/bin/rh7_upgrade_enm.sh --action get_upgrade_type --to_state_litp_version %s";
        final String productSetVersion = HAPropertiesReader.getProperty("product.set.version", "");
        final String enminstRpmDownloadCmd = HAPropertiesReader.getEnminstWgetCmd();
        final String enminstRpmExtractCmd = HAPropertiesReader.getEnminstExtractCmd();
        final String enminstReleaseUrl = HAPropertiesReader.getEnminstReleaseUrl() + productSetVersion;

        logger.info("enminstRpmExtractCmd : {}", enminstRpmExtractCmd);
        logger.info("productSetVersion : {}", productSetVersion);
        logger.info("enminstRpmDownloadCmd : {}", enminstRpmDownloadCmd);
        logger.info("enminstReleaseUrl : {}", enminstReleaseUrl);

        try {
            final CliShell msShell = new CliShell(HAPropertiesReader.getMS());
            final CliResult downloadResult = msShell.execute(enminstRpmDownloadCmd);
            logger.info("downloadResult : {}", downloadResult.getOutput());
            final CliResult extractResult = msShell.execute(enminstRpmExtractCmd);
            logger.info("extractResult : {}", extractResult.getOutput());
            if (productSetVersion.isEmpty()) {
                return;
            }

            try {
                final String jsonString = HAPropertiesReader.getResponseFromUrl(new URL(enminstReleaseUrl));
                JSONObject urlData = new org.json.JSONObject(jsonString);
                JSONArray jsonData = (JSONArray)urlData.get("deliverables");
                for ( int i=0; i<jsonData.length(); i++ ) {
                    JSONObject obj = jsonData.getJSONObject(i);
                    if (obj.getString("filename").contains("ERIClitp_CXP9024296") || obj.getString("productNumber").equalsIgnoreCase("CXP9024296")) {
                        logger.info("Json Object: {}", obj);
                        litpVersion = obj.getString("rstate").trim();
                        logger.info("litpVersion : {}", litpVersion);
                        break;
                    }
                }
            } catch (final Exception e) {
                logger.info("Json Message : {}", e.getMessage());
            }
            if (litpVersion.isEmpty()) {
                return;
            }

            final String getUGtypeCmd = String.format(ugTypeCommand, litpVersion);
            final CliResult upgradeTypeResult = msShell.executeAsRoot(getUGtypeCmd);
            final String result = upgradeTypeResult.getOutput();
            logger.info("getUGtypeCmd : {}", getUGtypeCmd);
            logger.info("Get upgradeTypeResult : {}", result);
            if (result.contains("Legacy Upgrade")) {
                physicalUpgradeType = "RH6";
            } else if (result.contains("RH7 uplift") || result.contains("RH7 upgrade off") || result.contains("RH6-RH7 hybrid")) {
                physicalUpgradeType = "RH7";
            }
            logger.info("physicalUpgradeType : {}", physicalUpgradeType);
        } catch (final Exception e) {
            logger.info("Error Message : {}", e.getMessage());
        }
    }
}
