package com.ericsson.adu;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Logger;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

@Path("adu")
public class ADUCloudNativeService {
    private static String ADU_DIR = "/usr/local/adu/";
    private static String ADU_DATA_DIR = "/var/adu/data/";
    private static Logger logger = Logger.getLogger("ADUCloudNativeService");
    private static List<Process> neo4jWatcher = new ArrayList<>();
    private static Process endpointWatcher;
    private static Process postgresWatcher;
    private static Process upgradeWatcher;
    private static Process ldapWatcher;
    private static Process imageWatcher;
    private static Process chaosTest;
    private static String nameSpace;
    private static String LOG_DIR = "/opt/tomcat/latest/logs/";

    /**
     * @param dependency
     * @return String
     * Get the Dependency down times list.
     * Also copies catalina.log and HighAvailability log files.
     */
    @GET
    @Path("endpoints/{dependency}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getEndpoints(@PathParam("dependency") String dependency) {
        logger.info("Check .. dependency = " + dependency);
        final StringBuilder builder = new StringBuilder();
        String  fileName = "";
        if (dependency.contains("catalina")) {
            readCatalinaFile(dependency);
            fileName = ADU_DATA_DIR + dependency;
        } else if (dependency.equalsIgnoreCase("endpoint.json")) {
            generateEndpointJsonFile();
            fileName = ADU_DATA_DIR + dependency;
        } else {
            fileName = (dependency.contains("log") || dependency.contains("properties")) ?  (ADU_DATA_DIR + dependency) : (ADU_DATA_DIR + dependency + ".csv");
        }

        final File file = new File(fileName);
        logger.info("Reading file data: " + fileName);
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                builder.append(scanner.nextLine());
                builder.append("\n");
            }
        } catch (Exception e) {
            logger.warning("ERROR: Failed to read file data: " + fileName);
            logger.warning(e.getMessage());
        }
        return builder.toString();
    }

    private void readCatalinaFile(String fileName) {
        Process copyProcess = null;
        try {
            readNamespace();
            String command = ADU_DIR + "readCatalinaFile.sh " + fileName;
            logger.info("start readCatalinaFile.sh command : " + command);
            copyProcess = Runtime.getRuntime().exec(command);
            while(copyProcess.isAlive()) {
                Thread.sleep(1000L);
                logger.info("copyProcess is running...");
            }
            logger.info("copyProcess completed.");
        } catch (Exception e) {
            logger.info("Error in running command : " + e.getMessage());
        } finally {
            if (copyProcess != null && copyProcess.isAlive()) {
                logger.info("copyProcess destroyed successFully");
                copyProcess.destroy();
            }
        }
    }

    @GET
    @Path("fts/graceperiod")
    @Produces(MediaType.TEXT_PLAIN)
    public String getFTSGracePeriod() {
        Process gracePeriodProcess = null;
        try {
            readNamespace();
            String command = ADU_DIR + "executeCommand.sh read_grace_period " + nameSpace;
            logger.info("start executeCommand.sh command : " + command);
            gracePeriodProcess = Runtime.getRuntime().exec(command);
            while(gracePeriodProcess.isAlive()) {
                Thread.sleep(1000L);
                logger.info("gracePeriodProcess is running...");
            }
            logger.info("gracePeriodProcess completed.");

            final BufferedReader br = new BufferedReader(new InputStreamReader(gracePeriodProcess.getInputStream()));
            final StringBuilder output = new StringBuilder();
            String line = "";

            while((line = br.readLine()) != null) {
                output.append(line).append("\n");
            }
            logger.info("output: " + output);
            return output.toString().trim();
        } catch (Exception e) {
            logger.info("Error in running command : " + e.getMessage());
        } finally {
            if (gracePeriodProcess != null && gracePeriodProcess.isAlive()) {
                logger.info("gracePeriodProcess destroyed successFully");
                gracePeriodProcess.destroy();
            }
        }
        return "";
    }

    /**
     * Get the PSV for deployment
     * @return String
     */
    @GET
    @Path("psv")
    @Produces(MediaType.TEXT_PLAIN)
    public String getFromPsv() {
        return getPsv();
    }

    private String getPsv() {
        Process readPsvProcess = null;
        try {
            readNamespace();
            String command = ADU_DIR + "executeCommand.sh read_psv " + nameSpace;
            logger.info("start executeCommand.sh command : " + command);
            readPsvProcess = Runtime.getRuntime().exec(command);
            while(readPsvProcess.isAlive()) {
                Thread.sleep(1000L);
                logger.info("readPsvProcess is running...");
            }
            logger.info("readPsvProcess completed.");

            final BufferedReader br = new BufferedReader(new InputStreamReader(readPsvProcess.getInputStream()));
            final StringBuilder output = new StringBuilder();
            String line = "";

            while((line = br.readLine()) != null) {
                output.append(line).append("\n");
            }
            logger.info("output: " + output);
            return output.toString().trim();
        } catch (Exception e) {
            logger.info("Error in running command : " + e.getMessage());
        } finally {
            if (readPsvProcess != null && readPsvProcess.isAlive()) {
                logger.info("readPsvProcess destroyed successFully");
                readPsvProcess.destroy();
            }
        }
        return "";
    }

    @GET
    @Path("imagepulldata/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getImagePullTime(@PathParam("name") String name) {
        logger.info("Getting image pull data for : " + name);
        String fileName = ADU_DATA_DIR + "image.properties";

        try {
            final PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
            final String pullTime = configuration.getProperty(name).toString();
            logger.info("Image pull time for " + name + " : " + pullTime);
            return pullTime;
        } catch (final Exception e) {
            logger.severe("Failed to get image pull time for : " + e.getMessage());
        }
        return "";
    }

    /**
     * Get cENM Upgrade status for Chart upgrade.
     * @return String
     */
    @GET
    @Path("upgrade/status")
    @Produces(MediaType.TEXT_PLAIN)
    public String getUpgradeRunningStatus() {
        final String fileName = ADU_DATA_DIR + "upgrade_status";
        final File file = new File(fileName);
        String status = "";
        try {
            final Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                status = scanner.nextLine();
            }
            logger.info("Upgrade status: " + status);
        } catch (final Exception e) {
            logger.warning("Upgrade status exception: " + e.getMessage());
        }
        return status;
    }

    /**
     * Get Custom Ingress Deployment are available or not
     */
    @GET
    @Path("ingress/available")
    @Produces(MediaType.TEXT_PLAIN)
    public boolean getIngressDeploymentAvailable() {
        final String fileName = ADU_DATA_DIR + "adu.properties";
        logger.info("Property fileName : " + fileName);
        try {
            final PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
            final String isIngressAvailable = configuration.getProperty("ingress.deployment.available").toString();
            logger.info("Read property ingress.deployment.available: " + isIngressAvailable);
            if (isIngressAvailable.contains("true")) {
                return true;
            }
        } catch (final Exception e) {
            logger.severe("Failed to read ingress from property file : " + e.getMessage());
        }
        return false;
    }


    /**
     * @param namespace
     * Set Namespace in adu.properties file. It will be used by watcher/upgrade scripts and webService.
     */
    @POST
    @Path("namespace/{name}")
    @Consumes(MediaType.TEXT_PLAIN)
    public void setNamespace(@PathParam("name") String namespace) {
        logger.info("Adding .. namespace = " + namespace);
        final String fileName = ADU_DATA_DIR + "adu.properties";
        logger.info("Property fileName : " + fileName);
        logger.info("PSV before Upgrade: " + getPsv());
        try {
            //Delete data files before UG/Regression
            deleteDataFiles();
            final PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
            configuration.setProperty("namespace", namespace);
            configuration.save();
            nameSpace = namespace;
            logger.info("Set namespace successfully: " + nameSpace);
        } catch (final Exception e) {
            logger.severe("Failed to write property file : " + e.getMessage());
        }
    }

    /**
     * @param type
     * Set Upgrade type to either enm or ccd.
     */
    @POST
    @Path("upgrade/type/{type}")
    @Consumes(MediaType.TEXT_PLAIN)
    public void setUpgradeType(@PathParam("type") String type) {
        logger.info("Adding .. Upgrade type = " + type);
        final String fileName = ADU_DATA_DIR + "adu.properties";
        logger.info("Property fileName : " + fileName);
        try {
            final PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
            configuration.setProperty("upgrade.type", type);
            configuration.save();
            logger.info("Set Upgrade type successfully: " + type);
        } catch (final Exception e) {
            logger.severe("Failed to write property file : " + e.getMessage());
        }
    }

    /**
     * @param users
     * Set ldap users.
     */
    @POST
    @Path("ldap/users/{users}")
    @Consumes(MediaType.TEXT_PLAIN)
    public void setLdapUsers(@PathParam("users") String users) {
        logger.info("Adding .. ldap users = " + users);
        final String fileName = ADU_DATA_DIR + "adu.properties";
        logger.info("Property fileName : " + fileName);
        try {
            final PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
            configuration.setProperty("ldap.users", users);
            configuration.save();
            logger.info("Set LDAP users successfully: " + users);
        } catch (final Exception e) {
            logger.severe("Failed to write property file : " + e.getMessage());
        }
    }

    /**
     * @param params
     * Set ldap params.
     */
    @POST
    @Path("ldap/param/{params}")
    @Consumes(MediaType.TEXT_PLAIN)
    public void setLdapParams(@PathParam("params") String params) {
        logger.info("Adding .. ldap params = " + params);
        final String fileName = ADU_DATA_DIR + "adu.properties";
        logger.info("Property fileName : " + fileName);
        try {
            final String[] paramList = params.split("~");
            final PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
            for (final String param : paramList) {
                if (param.contains("password")) {
                    configuration.setProperty("ldap.pass", param.split(":")[1]);
                } else if (param.contains("ipv4")) {
                    configuration.setProperty("ldap.ipv4", param.split(":")[1]);
                } else if (param.contains("port")) {
                    configuration.setProperty("ldap.port", param.split(":")[1]);
                } else if (param.contains("basedn")) {
                    configuration.setProperty("ldap.base.dn", param.split(":")[1]);
                }
            }
            configuration.save();
            logger.info("Set LDAP params successfully: " + params);
        } catch (final Exception e) {
            logger.severe("Failed to write property file : " + e.getMessage());
        }
    }

    /**
     * @param params
     * create/delete fan dummy file.
     */
    @POST
    @Path("fan/{params}")
    @Consumes(MediaType.TEXT_PLAIN)
    public String createDeleteFanFile(@PathParam("params") String params) {
        final String action = params.split(":")[0];
        final String fileName = params.split(":")[1];

        logger.info("FAN action : " + action);
        logger.info("FAN fileName : " + fileName);
        Process injectFileProcess = null;
        try {
            readNamespace();
            String command = ADU_DIR + "fanInjectFile.sh " + action + " " + fileName + " " + nameSpace;
            logger.info("start fanInjectFile.sh command : " + command);
            injectFileProcess = Runtime.getRuntime().exec(command);
            while(injectFileProcess.isAlive()) {
                Thread.sleep(1000L);
                logger.info("injectFileProcess is running...");
            }
            logger.info("injectFileProcess completed.");

            final BufferedReader br = new BufferedReader(new InputStreamReader(injectFileProcess.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line = "";

            while((line = br.readLine()) != null) {
                output.append(line).append("\n");
            }

            logger.info("output: " + output);
            if(output.toString().contains("success")) {
                logger.info("action [" + action + "] is successful.");
            } else {
                logger.info("action [" + action + "] is failed.");
            }
            return output.toString().trim();
        } catch (Exception e) {
            logger.info("Error in running command : " + e.getMessage());
        } finally {
            if (injectFileProcess != null && injectFileProcess.isAlive()) {
                logger.info("injectFileProcess destroyed successFully");
                injectFileProcess.destroy();
            }
        }
        return "failed to " + action + " FAN file";
    }

    /**
     * @param state
     * Set CCD Upgrade state to either running or stopped.
     * This parameter will be used to stop watchers at the end of CCD UG.
     */
    @POST
    @Path("upgrade/ccd/{state}")
    @Consumes(MediaType.TEXT_PLAIN)
    public void setCCDUpgradeState(@PathParam("state") String state) {
        logger.info("Adding .. CCD Upgrade state = " + state);
        final String fileName = ADU_DATA_DIR + "ccd_status";
        logger.info("CCD status fileName : " + fileName);
        try {
            final File file = new File(fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileUtils.writeStringToFile(file, state);
            logger.info("Set CCD Upgrade state successfully to : " + state);
        } catch (final Exception e) {
            logger.severe("Failed to write CCD Upgrade state : " + e.getMessage());
        }
    }

    /**
     * Start/stop Upgrade status check script.
     * @param action
     * @return boolean
     */
    @POST
    @Path("script/upgrade/{action}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public boolean startStopUpgradeWatcherScript(@PathParam("action") String action) {
        logger.info("Execute startStopUpgradeWatcherScript .. action = " + action);
        try {
            if (action.equalsIgnoreCase("start")) {
                Runtime.getRuntime().exec(ADU_DIR + "upgradeInit.sh start");
            } else if (action.equalsIgnoreCase("stop")) {
                upgradeWatcher = Runtime.getRuntime().exec(ADU_DIR + "upgradeInit.sh stop");
            }
        } catch (final Exception e) {
            logger.warning("Error in upgrade watcher action" + action + ":" + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Start/stop dependency watcher scripts.
     * @param action
     * @return boolean
     */
    @POST
    @Path("script/{action}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public boolean startStopEndpointWatcherScript(@PathParam("action") String action) {
        logger.info("Execute startStopWatcherScript .. action = " + action);
        readNamespace();
        if (nameSpace == null || nameSpace.isEmpty()) {
            logger.warning("Namespace is empty: " + nameSpace );
            return false;
        }
        try {
            if (action.equalsIgnoreCase("start")) {
                if (endpointWatcher != null || imageWatcher != null || ldapWatcher != null || postgresWatcher != null) {
                    return false;
                }
                endpointWatcher = Runtime.getRuntime().exec(ADU_DIR + "aduWatcher.py");
                ldapWatcher = Runtime.getRuntime().exec(ADU_DIR + "ldapWatcher.py");
                imageWatcher = Runtime.getRuntime().exec(ADU_DIR + "getImagePullTime.py");
                postgresWatcher = Runtime.getRuntime().exec(ADU_DIR + "postgresWatcher.py " + nameSpace);

                ApiClient client = ClientBuilder.defaultClient();
                Configuration.setDefaultApiClient(client);
                final CoreV1Api api = new CoreV1Api();
                final V1PodList podList = api.listNamespacedPod(nameSpace, null, null, null, null, null, null, null, null);
                for (final V1Pod item : podList.getItems()) {
                    if (item.getMetadata().getName().contains("neo4j-0") || item.getMetadata().getName().contains("neo4j-1") || item.getMetadata().getName().contains("neo4j-2") ) {
                        logger.info("pod name :" + item.getMetadata().getName());
                        logger.info("pod ip :" + item.getStatus().getPodIP());
                        final String neo4jCommand = ADU_DIR + "neo4jWatcher.py " + item.getMetadata().getName() + " " + item.getStatus().getPodIP() + " " + nameSpace;
                        try {
                            neo4jWatcher.add(Runtime.getRuntime().exec(neo4jCommand));
                        } catch (final Exception e) {
                            logger.warning(e.getMessage());
                        }
                    }
                }
            } else if (action.equalsIgnoreCase("stop")) {
                if (endpointWatcher != null && endpointWatcher.isAlive()) {
                    endpointWatcher.destroy();
                    endpointWatcher = null;
                }
                if (ldapWatcher != null && ldapWatcher.isAlive()) {
                    ldapWatcher.destroy();
                    ldapWatcher = null;
                }
                if (imageWatcher != null && imageWatcher.isAlive()) {
                    imageWatcher.destroy();
                    imageWatcher = null;
                }
                if (postgresWatcher != null && postgresWatcher.isAlive()){
                    postgresWatcher.destroy();
                    postgresWatcher = null;
                }
                for (final Process process : neo4jWatcher) {
                    if (process!=null && process.isAlive()) {
                        process.destroy();
                    }
                }
                neo4jWatcher.clear();
                return true;
            }
        } catch (final Exception e) {
            logger.warning("Error in action" + action + ":" + e.getMessage());
            return false;
        }
        return true;
    }

    @POST
    @Path("chaos/{action}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public boolean configChaosMesh(@PathParam("action") String action) {
        logger.info("Chaos-mesh .. " + action);
        final String command = ADU_DIR + "configChaosMesh.sh " + action + " " + nameSpace;
        Process chaosProcess;
        try {
            logger.info("start chaos-mesh command : " + command);
            chaosProcess = Runtime.getRuntime().exec(command);
            while(chaosProcess.isAlive()) {
                Thread.sleep(2000L);
                logger.info("chaosProcess is running... : " + action);
            }
            logger.info("chaosProcess completed.");
            if (chaosProcess.exitValue() == 0) {
                return true;
            }

        } catch (final Exception e) {
            logger.severe("Failed to config chaos-mesh : " + e.getMessage());
        }
        return false;
    }

    /**
     * Get neo4j pod list for downtime calculation.
     * @return String
     */
    @GET
    @Path("neo4j")
    @Produces(MediaType.TEXT_PLAIN)
    public String getNeo4jList() {
        final StringBuilder builder = new StringBuilder();
        try {
            final ApiClient client = ClientBuilder.defaultClient();
            Configuration.setDefaultApiClient(client);
            final CoreV1Api api = new CoreV1Api();
            readNamespace();
            final V1PodList podList = api.listNamespacedPod(nameSpace, null, null, null, null, null, null, null, null);
            for (final V1Pod item : podList.getItems()) {
                if (item.getMetadata().getName().contains("neo4j-0") || item.getMetadata().getName().contains("neo4j-1") || item.getMetadata().getName().contains("neo4j-2") ) {
                    builder.append(item.getMetadata().getName());
                    builder.append("\n");
                }
            }
        } catch (final Exception e) {
            logger.info("Error in fetching neo4j pod names :  " + e.getMessage());
        }
        return builder.toString();
    }

    /**
     * Get esm/cnom pod name.
     *
     * @return String
     */
    @GET
    @Path("esm")
    @Produces(MediaType.TEXT_PLAIN)
    public String getEsmPod() {
        try {
            final ApiClient client = ClientBuilder.defaultClient();
            Configuration.setDefaultApiClient(client);
            final CoreV1Api api = new CoreV1Api();
            readNamespace();
            final V1PodList podList = api.listNamespacedPod(nameSpace, null, null, null, null, null, null, null, null);
            for (final V1Pod item : podList.getItems()) {
                if (item.getMetadata().getName().contains("eric-cnom-server") || item.getMetadata().getName().contains("eric-esm-server")) {
                    return item.getMetadata().getName();
                }
            }
        } catch (final Exception e) {
            logger.info("Error in fetching esm pod name :  " + e.getMessage());
        }
        return "";
    }

    /**
     * Read namespace from property file in case pod restart/worker node restart.
     */
    private void readNamespace() {
        if (nameSpace == null || nameSpace.isEmpty()) {
            final String fileName = ADU_DATA_DIR + "adu.properties";
            try {
                final PropertiesConfiguration configuration = new PropertiesConfiguration(fileName);
                nameSpace = configuration.getProperty("namespace").toString();
                logger.info("Read/Set namespace successfully: " + nameSpace);
            } catch (final Exception e) {
                logger.severe("Failed to read property file: " + fileName );
                logger.severe(e.getMessage());
            }
        }
    }

    private void deleteDataFiles() {
        try {
            final File dataDir = new File(ADU_DATA_DIR);
            final File []fileList = dataDir.listFiles();

            if (fileList != null) {
                for (final File file : fileList) {
                    if (file.getName().endsWith(".csv")) {
                        boolean isSuccess = file.delete();
                        logger.info("Data file " + file.getName() + " delete is Success : " + isSuccess);
                    }
                }
            }
        } catch (final Exception e) {
            logger.warning("Failed to delete data files : " + e.getMessage());
        }
    }

    /**
     * Start chaos regression test.
     */
    @POST
    @Path("chaos/regression/start/{test_data}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public boolean startChaosRegressionTest(@PathParam("test_data") String testData) {
        logger.info("Starting Chaos Regression with data : " + testData);
        try {
            final String testType = testData.split(":")[0];
            final String testApp = testData.split(":")[1];
            final String faultDuration = testData.split(":")[2];
            final String containerName = testData.split(":")[3];
            final String mode = testData.split(":")[4];

            final String testCaseName = testType + "-" + testApp;
            final String configFile = ADU_DATA_DIR + testCaseName + ".yaml";
            logger.info("Chaos Regression config file : " + configFile);
            final File file = new File (configFile);
            try {
                if (file.delete()) {
                    logger.info("Older config file: " + configFile + " deleted successfully.");
                }
            } catch (final Exception e) {
                //File not exist.
            }
            final String chaosConfigBuffer = configBufferBuilder(testType, testApp, faultDuration, containerName, mode);
            if (chaosConfigBuffer.isEmpty()) {
                return false;
            }
            final BufferedWriter configWriter = new BufferedWriter(new FileWriter(configFile));
            configWriter.write(chaosConfigBuffer);
            configWriter.close();
            chaosTest = Runtime.getRuntime().exec("/usr/local/bin/kubectl apply -f " + configFile);
            logger.info("ChaosTest " + testCaseName + " started successfully.");
        } catch (final Exception e) {
            logger.severe("Failed to execute test data: " + testData);
            logger.severe("ErrorMessage: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Stop chaos regression test.
     */
    @POST
    @Path("chaos/regression/stop/{test_data}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public boolean stopChaosRegressionTest(@PathParam("test_data") String testData) {
        logger.info("Stopping Chaos Regression with data : " + testData);
        try {
            final String testType = testData.split(":")[0];
            final String testApp = testData.split(":")[1];
            final String testCaseName = testType + "-" + testApp;
            final String configFile = ADU_DATA_DIR + testCaseName + ".yaml";
            final String command = "/usr/local/bin/kubectl delete -f " + configFile;
            logger.info("Executing delete test command : " + command);
            chaosTest = Runtime.getRuntime().exec(command);
            Thread.sleep(3000);
            logger.info("ChaosTest " + testCaseName + " stopped successfully.");
            try {
                if (chaosTest != null && chaosTest.isAlive()) {
                    chaosTest.destroy();
                }
            } catch (final Exception ex) {
                logger.info("Chaos process delete : " + ex.getMessage());
            }
        } catch (final Exception e) {
            logger.warning("Exception in  stopChaosRegressionTest: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Get fts downtimes.
     */
    @GET
    @Path("smrs/dt/windows")
    @Consumes(MediaType.TEXT_PLAIN)
    public String getFtsDowntimeWindows() {

        logger.info("getFtsDowntimeWindows ...");
        StringBuilder output = new StringBuilder();
        try {
            final String command = ADU_DIR + String.format("getFtsDowntimeWindows.sh %s", nameSpace);
            logger.info("Executing command : " + command);
            final Process execProcess = Runtime.getRuntime().exec(command);
            Thread.sleep(5000);
            final BufferedReader br = new BufferedReader(new InputStreamReader(execProcess.getInputStream()));
            String line = "";

            while((line = br.readLine()) != null) {
                output.append(line).append("\n");
            }
            logger.info("getFtsDowntimeWindows output: " + output);
            if (execProcess.isAlive()) {
                execProcess.destroy();
            }
        } catch (final Exception e) {
            logger.warning("Failed to execute remove backup command from SMRS. : " + e.getMessage());
        }
        return output.toString();
    }

    /**
     * Delete smrs backup file, if any.
     */
    @POST
    @Path("smrs/remove/{nodeData}")
    @Consumes(MediaType.TEXT_PLAIN)
    public void removeSmrsBackupFiles(@PathParam("nodeData") String nodeData) {

        logger.info("Removing smrs backup files .. using nodeData : " + nodeData);
        final String nodeType = nodeData.split(":")[0];
        final String nodeName = nodeData.split(":")[1];
        logger.info("NodeType : " + nodeType + ", NodeName : " + nodeName);
        try {
            final String command = ADU_DIR + String.format("smrsBackupFileCleanup.sh %s %s %s", nameSpace, nodeType, nodeName);
            logger.info("Executing command : " + command);
            final Process execProcess = Runtime.getRuntime().exec(command);
            Thread.sleep(5000);
            if (execProcess.isAlive()) {
                execProcess.destroy();
            }
        } catch (final Exception e) {
            logger.warning("Failed to execute remove backup command from SMRS. : " + e.getMessage());
        }
    }

    /**
     * Generate Config file content to write into file.
     * @param testType
     * @param testApp
     * @return
     */
    private String configBufferBuilder(final String testType,
                                       final String testApp,
                                       final String faultDuration,
                                       final String containerName,
                                       final String mode) {
        try {
            final String testCaseName = testType + "-" + testApp;
            final String resourceTemplate;
            if (testApp.contains("postgres")) {
                resourceTemplate = "chaos/" + testType + "-postgres-template.yaml";
            } else {
                resourceTemplate = "chaos/" + testType + "-template.yaml";
            }
            logger.info("Using template file : " + resourceTemplate);
            final InputStream inputTemplate = getClass().getClassLoader().getResourceAsStream(resourceTemplate);
            if (inputTemplate == null) {
                logger.severe("Template file doesn't exist: " + resourceTemplate);
                return "";
            }
            String chaosConfigBuffer = IOUtils.toString(inputTemplate);
            chaosConfigBuffer = chaosConfigBuffer.replace("$test_name", testCaseName);
            chaosConfigBuffer = chaosConfigBuffer.replace("$namespace", nameSpace);
            chaosConfigBuffer = chaosConfigBuffer.replace("$mode", mode);

            if (testApp.contains("neo4j")) {
                chaosConfigBuffer = chaosConfigBuffer.replace("$selector", "statefulset.kubernetes.io/pod-name");
                if (testApp.contains("leader")) {
                    chaosConfigBuffer = chaosConfigBuffer.replace("$app_name", getLeaderFollowerHostName("leader"));
                } else if (testApp.contains("follower")) {
                    chaosConfigBuffer = chaosConfigBuffer.replace("$app_name", getLeaderFollowerHostName("follower"));
                }
            } else if (testApp.contains("postgres")) {
                chaosConfigBuffer = chaosConfigBuffer.replace("$selector", "app");
                chaosConfigBuffer = chaosConfigBuffer.replace("$app_name", "postgres");
                chaosConfigBuffer = chaosConfigBuffer.replace("$role_selector", "role");
                if (testApp.contains("master")) {
                    chaosConfigBuffer = chaosConfigBuffer.replace("$role_name", "master");
                } else if (testApp.contains("replica")) {
                    chaosConfigBuffer = chaosConfigBuffer.replace("$role_name", "replica");
                }
            } else if (testApp.contains("ingress")) {
                chaosConfigBuffer = chaosConfigBuffer.replace("$selector", "app.kubernetes.io/name");
                chaosConfigBuffer = chaosConfigBuffer.replace("$app_name", testApp);
            } else  {
                chaosConfigBuffer = chaosConfigBuffer.replace("$selector", "app");
                chaosConfigBuffer = chaosConfigBuffer.replace("$app_name", testApp);
            }

            switch (testType) {
                case "pod-kill":
                    //TBA
                    break;
                case "pod-failure":
                    chaosConfigBuffer = chaosConfigBuffer.replace("$duration", faultDuration);
                    break;
                case "container-kill":
                    chaosConfigBuffer = chaosConfigBuffer.replace("$container_name", containerName);
                    break;
                default:
                    logger.severe("Unknown test case type: " + testType);
                    break;
            }
            logger.info("chaosConfigBuffer: " + chaosConfigBuffer);
            return chaosConfigBuffer;
        } catch (final Exception e) {
            logger.severe("Exception in configBufferBuilder : " + e.getMessage());
            return "";
        }
    }

    private Map<String, String> getNeoPodMap() {
        final Map<String, String> neoPodMap = new HashMap<>();
        try {
            ApiClient client = ClientBuilder.defaultClient();
            Configuration.setDefaultApiClient(client);
            final CoreV1Api api = new CoreV1Api();
            final V1PodList podList = api.listNamespacedPod(nameSpace, null, null, null, null, null, null, null, null);
            for (final V1Pod item : podList.getItems()) {
                if (item.getMetadata().getName().contains("neo4j-0") || item.getMetadata().getName().contains("neo4j-1") || item.getMetadata().getName().contains("neo4j-2") ) {
                    logger.info("pod name :" + item.getMetadata().getName());
                    logger.info("pod ip :" + item.getStatus().getPodIP());
                    neoPodMap.put(item.getMetadata().getName(), item.getStatus().getPodIP());
                }
            }
        } catch (final Exception e) {
            logger.warning("Message: " + e.getMessage());
        }
        logger.info("neoPodMap : " + neoPodMap);
        return neoPodMap;
    }

    private String getLeaderFollowerHostName(final String neoType) {
        final Map<String, String> neoMap = getNeoPodMap();
        String hostName;
        String leaderHost = "";
        try {
            for (final Map.Entry<String, String> entry : neoMap.entrySet()) {
                final String command = String.format("curl -s --user neo4j:Neo4jadmin123 -m 5 http://%s:7474/db/dps/cluster/writable", entry.getValue());
                try {
                    final Process neoProcess = Runtime.getRuntime().exec(command);
                    final BufferedReader in = new BufferedReader(new InputStreamReader(neoProcess.getInputStream()));
                    final BufferedReader error = new BufferedReader(new InputStreamReader(neoProcess.getErrorStream()));
                    final String outputString = in.readLine();
                    logger.info("exec output : " + outputString);

                    String errorString;
                    while ((errorString = error.readLine()) != null) {
                        logger.info("error output : " + errorString);
                    }

                    if (outputString != null && outputString.contains("true")) {
                        leaderHost = entry.getKey();
                        logger.info("Host: " + leaderHost + " is neo leader.");
                        break;
                    }
                } catch (IOException ex) {
                    logger.warning("Message : " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Message : " + e.getMessage());
        }
        if (neoType.contains("leader")) {
            hostName = leaderHost;
        } else {
            neoMap.remove(leaderHost);
            Random generator = new Random();
            final Object[] followers = neoMap.keySet().toArray();
            logger.info("Neo4j followers list : " + Arrays.toString(followers));
            Object randomFollower = followers[generator.nextInt(followers.length)];
            hostName = randomFollower.toString();
        }
        return hostName;
    }

    private void generateEndpointJsonFile() {
        try {
            logger.info("PSV after Upgrade: " + getPsv());
            final String command = ADU_DIR + "getEndpointJson.py";
            logger.info("Command : " + command);
            final Process epJson = Runtime.getRuntime().exec(command);
            logger.info("epJson is alive: " + epJson.isAlive());
            Thread.sleep(7000);
            if (epJson.isAlive()) {
                logger.info("epJson process is still alive .. terminating.");
                epJson.destroy();
            }
        } catch (final Exception e) {
            logger.warning("Failed to write EP JSON file : " + e.getMessage());
        }
    }

}
