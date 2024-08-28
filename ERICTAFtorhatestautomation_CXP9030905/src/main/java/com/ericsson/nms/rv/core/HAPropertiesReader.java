package com.ericsson.nms.rv.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;
import javax.net.ssl.HttpsURLConnection;
import java.util.Scanner;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.ericsson.cifwk.taf.data.Ports;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.cifwk.taf.tools.http.constants.HttpStatus;
import com.ericsson.nms.rv.core.esm.CnomSystemMonitor;
import com.ericsson.nms.rv.core.fan.FileAccessNBI;
import com.ericsson.nms.rv.core.fan.FileLookupService;
import com.ericsson.nms.rv.core.fm.FaultManagementBsc;
import com.ericsson.nms.rv.core.fm.FaultManagementRouter;
import com.ericsson.nms.rv.core.ldap.ComAaLdap;
import com.ericsson.nms.rv.core.pm.PerformanceManagementRouter;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.shm.ShmSmrs;
import com.ericsson.nms.rv.core.upgrade.CloudDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.upgrade.CloudNativeDependencyDowntimeHelper;
import com.ericsson.nms.rv.core.util.CertUtil;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.RegressionUtils;
import com.ericsson.nms.rv.core.util.TestCase;
import com.ericsson.nms.rv.core.util.UpgradeUtils;
import net.minidev.json.JSONValue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.data.HostType;
import com.ericsson.cifwk.taf.data.User;
import com.ericsson.cifwk.taf.data.UserType;
import com.ericsson.nms.rv.core.amos.Amos;
import com.ericsson.nms.rv.core.cm.ConfigurationManagement;
import com.ericsson.nms.rv.core.cm.bulk.CmBulkExport;
import com.ericsson.nms.rv.core.cm.bulk.CmImportToLive;
import com.ericsson.nms.rv.core.esm.EnmSystemMonitor;
import com.ericsson.nms.rv.core.fm.FaultManagement;
import com.ericsson.nms.rv.core.launcher.AppLauncher;
import com.ericsson.nms.rv.core.nbi.verifier.NbiAlarmVerifier;
import com.ericsson.nms.rv.core.nbi.verifier.NbiCreateSubsVerifier;
import com.ericsson.nms.rv.core.netex.NetworkExplorer;
import com.ericsson.nms.rv.core.pm.PerformanceManagement;
import com.ericsson.nms.rv.core.shm.SoftwareHardwareManagement;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.core.um.UserManagement;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.nms.rv.taf.tools.host.Database;
import com.ericsson.nms.rv.taf.tools.host.Service;
import com.ericsson.nms.rv.taf.tools.properties.HaToolsProperties;
import com.ericsson.nms.rv.taf.tools.properties.HaToolsProperties.PropertiesBuilder;
import com.ericsson.nms.rv.taf.tools.properties.PropertiesReader;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class HAPropertiesReader extends PropertiesReader {
    private static final String DOC_URL = "https://atvdit.athtem.eei.ericsson.se/api/documents/";
    private static final String SED_URL = "https://atvdit.athtem.eei.ericsson.se/api/deployments?q=name=";
    public static final String DELTA = "DELTA";
    private static final String FUNCTIONAL_AREA = "functional.area.%s";
    private static final String FALSE = "false";
    private static Host WORKLOAD;
    private static Logger logger = LogManager.getLogger(HAPropertiesReader.class);
    private static final Map<String, Boolean> functionalAreasMap = new HashMap<>();
    public static final Map<String, Long> dependencyThreshholdCloud = new HashMap<>();
    public static final Map<String, Long> dependencyThreshholdCloudNative = new HashMap<>();
    public static final Map<String, Long> dependencyThreshholdCCD = new HashMap<>();
    public static final Map<String, Long>  dependencyThreshholdVio = new HashMap<>();
    public static final Map<String, Long> dependencyThreshholdPhysical = new HashMap<>();
    public static final HashMap<String, List<String>> multiInstanceMap = new HashMap<>();
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String PRODUCT_NAME = "ERICTAFtorhatestautomation_CXP9030905";
    public static  boolean VIOENV ;
    public static boolean isWatcherConnectionFailed = false;
    public static String ftsOffDateCmd = "sudo /bin/egrep -h 'Generating.SSH2.RSA.host.key' /var/log/messages | /bin/cut -b -15 | tail -n 1";
    /**
     * Functional Area Dependencies.
     */
    public static final String ELASTICSEARCH = "elasticsearch";
    public static final String ELASTICSEARCHPROXY = "elasticsearchproxy";
    public static final String ESHISTORY = "eshistory";
    public static final String HAPROXY = "haproxy";
    public static final String SSO = "sso";
    public static final String OPENDJ="opendj";
    public static final String SECSERV="secserv";
    public static final String JMS = "jms";
    public static final String OPENIDM = "openidm";
    public static final String POSTGRES = "postgres";
    public static final String MODELS = "models";
    public static final String NFSAMOS = "nfsamos";
    public static final String NFSBATCH = "nfsbatch";
    public static final String NFSCONFIGMGT = "nfsconfigmgt";
    public static final String NFSCUSTOM = "nfscustom";
    public static final String NFSDATAD = "nfsdata";
    public static final String NFSDDCDATA = "nfsddcdata";
    public static final String NFSHCDUMPS = "nfshcdumps";
    public static final String NFSHOME = "nfshome";
    public static final String NFSMDT = "nfsmdt";
    public static final String NFSNOROLLBACK = "nfsnorollback";
    public static final String NFSPM1 = "nfspm1";
    public static final String NFSPM2 = "nfspm2";
    public static final String NFSPMLINKS = "nfspmlinks";
    public static final String NFSSMRS = "nfssmrs";
    public static final String NEO4J_LEADER = "neo4jLeader";
    public static final String NEO4J_FOLLOWER = "neo4jFollower";
    public static final String NEO4J = "neo4j";
    public static final String VISINAMINGNB = "visinamingnb";
    public static final String HAPROXY_INT = "haproxy_int";
    public static final String INGRESS = "ingress";
    public static final String CNOM = "cnom";
    public static final String SYS = "sys";
    public static final String FTS = "filetransferservice";

    private static final Map<String, Map<String, Long>> functionalAreasThresholdMap = new HashMap<>();
    private static final Map<String, List<String>> applicationDependencyMap = new HashMap<>();
    private static final Map<String, List<String>> appImageDependencyMap = new HashMap<>();
    private static final Map<String, List<String>> infraImageDependencyMap = new HashMap<>();
    /**
     * Functional area keys.
     */
    private static final String AMOS = "amos";
    private static final String APPLAUNCH = "applaunch";
    private static final String CMBE = "cmbe";
    private static final String CMBIL = "cmbil";
    private static final String CM = "cm";
    private static final String ESM = "esm";
    private static final String FM = "fm";
    private static final String FMR = "fmr";
    private static final String FMB = "fmb";
    private static final String PMR = "pmr";
    private static final String NBIVA = "nbiva";
    private static final String NBIVS = "nbivs";
    private static final String NETEX = "netex";
    private static final String PM = "pm";
    private static final String SHM = "shm";
    private static final String UM = "um";
    private static final String CESM = "cesm";
    private static final String SYSTEM = "system";
    private static final String AALDAP = "aaldap";
    private static final String SMRS = "smrs";
    private static final String FAN = "fan";
    private static final String FLS = "fls";

    private static final String tafClusterId;
    private static String FROM_ISO;
    private static final String TO_ISO;
    private static String tafConfigDitDeploymentName = "";
    private static Host MS;
    private static Host SVC1;
    private static String enmUsername;
    private static String enmPassword;
    private static String hatoolsUrl;
    private static String nexusUrl;
    private static int timeToSleep;
    private static long waitForUpgradeToStart;
    private static int ropTime;
    private static Boolean runOnce;
    private static Long maxDisruptionTime;
    private static String netsimConfig;
    private static String radioNetsimConfig;
    private static int numberOfAlarms;
    private static int timeToWaitForServiceOnline;
    private static Long pmTimeToWaitForSubscription;
    private static Long cmbeTimeToWaitJobComplete;
    private static long fmWaitingTimePerAlarm;
    private static Long upgradeStartTime;
    private static Long timeToWaitNodesToSync;
    private static long httpSessionTimeout;
    public static boolean disableCnomUsecase = false;
    public static boolean isEsmPodPresentOnServer = false;
    private static boolean useExternalVm = false;
    private static String externalVmType = "";
    private static String externalVmIP = "";

    private static String sfsIlo1Ip;
    private static String sfsIlo2Ip;
    private static String sfsIloUsername;
    private static String sfsIloPassword;
    private static String sfsClusterUsername;
    private static String sfsClusterPassword;
    private static String sfsClusterIp;
    private static String wlp;
    private static boolean envCloud;
    private static boolean envCloudNative;
    private static boolean isEnmOnRack;
    private static String testType;
    private static HttpRestServiceClient httpRestServiceClientCloudNative;
    private static HttpRestServiceClient httpRestServiceExternalClient;

    public static final Map<String, String> appMap = new HashMap<>();
    public static final Map<FunctionalArea, String> areaMap = new HashMap<>();
    public static final Map<FunctionalArea, String> cleanupMap = new HashMap<>();
    public static boolean neoconfig40KFlag;
    public static boolean neoconfig60KFlag;
    public static final Map<String, NavigableMap<Date, Date>> ignoreDTMap = new ConcurrentHashMap<>();//delayed response map
    public static final Map<String, Map<Date, Date>> ignoreDtTimeOutMap = new ConcurrentHashMap<>();//timeout clash with (delayed + neo4j) windows
    public static final Map<String, Long> ignoreDtTotalSecs = new ConcurrentHashMap<>();//timeout clash delayed seconds
    public static final Map<String, Map<Date, Date>> ignoreErrorDtMap = new ConcurrentHashMap<>();//Total(error + timeout) clash with neo4j windows
    public static final Map<String, Long> ignoreErrorClashNeo4jTotalSecs = new ConcurrentHashMap<>();//error clash neo4j seconds
    public static final Map<String, Long> ignoreTimeoutClashNeo4jTotalSecs = new ConcurrentHashMap<>();//timeout clash neo4j seconds

    public static boolean HaValue;
    public static String cloudIngressHost;
    public static int neo4jVersion;
    public static String path;
    public static String cEnmName;
    public static String cEnmChaosTestType;
    public static String cEnmChaosAppName;
    public static String cEnmChaosFaultFreq;
    public static String cEnmChaosFaultDuration;
    public static String cEnmChaosContainerName;
    public static String cEnmChaosMode;

    public static final String AMOSAPP = "Amos";
    public static final String CONFIGURATIONMANGEMENT = "ConfigurationManagement";
    public static final String PERFORMANCEMANAGEMENT = "PerformanceManagement";
    public static final String FAULTMANAGEMENT = "FaultManagement";
    public static final String FILELOOKUPSERVICE = "FileLookupService";
    public static final String FAULTMANAGEMENTROUTER = "FaultManagementRouter";
    public static final String FAULTMANAGEMENTBSC = "FaultManagementBsc";
    public static final String PERFORMANCEMANAGEMENTROUTER = "PerformanceManagementRouter";
    public static final String SOFTWAREHARDWAREMANAGEMENT = "SoftwareHardwareManagement";
    public static final String NBIALARMVERIFIER = "NbiAlarmVerifier";
    public static final String NBICREATESUBSVERIFIER = "NbiCreateSubsVerifier";
    public static final String CMBULKEXPORT = "CmBulkExport";
    public static final String CMBULKIMPORTTOLIVE = "CmImportToLive";
    public static final String APPLAUNCHER = "AppLauncher";
    public static final String USERMANAGEMENT = "UserManagement";
    public static final String NETWORKEXPLORER = "NetworkExplorer";
    public static final String ENMSYSTEMMONITOR = "EnmSystemMonitor";
    public static final String CNOMSYSTEMMONITOR = "CnomSystemMonitor";
    public static final String SYSTEMVERIFIER = "SystemVerifier";
    public static final String COMAALDAP = "ComAaLdap";
    public static final String SHMSMRS = "ShmSmrs";
    public static final String FILEACCESSNBI = "FileAccessNBI";
    public static String haUsername;
    public static String haPassword;
    public static int appRestTimeOut;
    public static int pmRestTimeOut;
    public static int enmLoginLogoutRestTimeOut;
    public static long cloudUgStartTimeLimit;
    public static long cloudUgFinishTimeLimit;
    public static long aduDurationOnDemand;
    public static List<String> appImageList = new ArrayList<>();
    public static List<String> infraImageList = new ArrayList<>();
    public static String fmVipAddressFromValuesDoc = "";
    public static String secServLoadBalancerIPFromValuesDoc = "";

    static {
        dependencyThreshholdPhysical.putAll(loadThresholdValues("infra", "infra.phy"));
        dependencyThreshholdCloud.putAll(loadThresholdValues("infra", "infra.cloud"));
        dependencyThreshholdVio.putAll(loadThresholdValues("infra", "infra.vio"));
        dependencyThreshholdCloudNative.putAll(loadThresholdValues("infra", "infra.cenm"));
        dependencyThreshholdCCD.putAll(loadThresholdValues("infra", "infra.ccd"));

        infraImageList.add("eric-enm-init-container");
        infraImageList.add("eric-enmsg-openidm");
        infraImageList.add("eric-enm-securestorage-init-base");
        infraImageList.add("eric-enm-monitoring-jre");
        infraImageList.add("eric-enmsg-jmsserver");
        infraImageList.add("eric-enm-monitoring-eap7");
        infraImageList.add("eric-enmsg-sso");
        infraImageList.add("eric-enmsg-sso-httpd");
        infraImageList.add("eric-enm-sso-core-token-service");
        infraImageList.add("eric-enmsg-opendj-init");
        infraImageList.add("eric-enmsg-opendj");
        infraImageList.add("eric-data-search-engine");
        infraImageList.add("eric-data-document-database-pg13");
        infraImageList.add("eric-data-document-database-metrics");
        infraImageList.add("eric-enm-neo4j-extension-plugin");
        infraImageList.add("graphdb-n4j");
        infraImageList.add("eric-oss-ingress-controller-nx");
        infraImageList.add("eric-enmsg-visinaming-nb");

        appImageList.add("eric-enmsg-amos");
        appImageList.add("eric-enmsg-amos-httpd");
        appImageList.add("eric-enmsg-networkexplorer");
        appImageList.add("eric-enmsg-networkexplorer-httpd");
        appImageList.add("eric-enmsg-shm-core-service");
        appImageList.add("eric-enmsg-shm-core-service-httpd");
        appImageList.add("eric-enmsg-shmservice");
        appImageList.add("eric-enmsg-shmservice-httpd");
        appImageList.add("eric-enmsg-cmservice");
        appImageList.add("eric-enmsg-cmservice-httpd");
        appImageList.add("eric-enmsg-fm-service");
        appImageList.add("eric-enmsg-fm-service-httpd");
        appImageList.add("eric-enmsg-pmservice");
        appImageList.add("eric-enmsg-pmservice-httpd");

        tafClusterId = getProperty("taf.clusterId", "-");
        tafConfigDitDeploymentName = getProperty("taf.config.dit.deployment.name", "");
        envCloud = !tafConfigDitDeploymentName.isEmpty();
        envCloudNative = Boolean.parseBoolean(getProperty("env.cloud.native", "false"));

        if(envCloudNative) {
            FROM_ISO = getProperty("from.state.product.set.version", "-");
            TO_ISO = getProperty("product.set.version", "-");
        } else {
            FROM_ISO = getProperty("from_iso", "-");
            TO_ISO = getProperty("to_iso", "-");
        }
        logger.info("FROM_ISO : {}", FROM_ISO);
        logger.info("TO_ISO : {}", TO_ISO);

        WORKLOAD = HostConfigurator.getWorkload();
        logger.info("WORKLOAD : {}", WORKLOAD);
        try (final InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("taf_properties/hosts.properties")) {
            PropertiesReader.properties.load(in);
            if (envCloudNative) {
                //cENM comment. Add dummy MS for taf initialization on cENM env.
                final String worker = getProperty("env.worker.dummy", "");
                logger.info("Cloud Native env.");
                MS = new Host();
                MS.setIp(worker);
                MS.setType(HostType.MS);
                MS.setUser("root");
                MS.setPass("12shroot");
            } else if (envCloud) {
                final Path pemFile = CertUtil.getPemFilePath();
                logger.info("PemFile : {}", pemFile.toString());
                HaValue = getHaValueFromJson();
                if (!HaValue) {
                    MS = HostConfigurator.getHost("enm_laf_1");
                } else {
                    MS = HostConfigurator.getHost("vnflaf_vip_1");
                }
                MS.setType(HostType.MS);
                logger.info("MS : {}", MS);
                final List<User> users = new ArrayList<>();
                users.add(new User(getProperty("cloud.vnflaf.username", "").trim(), getProperty("cloud.vnflaf.password", "").trim(), UserType.ADMIN));
                MS.setUsers(users);
                logger.info("Host ip : {}", MS.getIp());
                path = "/home/" + MS.getUser();
            } else {
                MS = HostConfigurator.getMS();
                SVC1 = HostConfigurator.getSVC1();
                final CliShell shell = new CliShell(MS);
                logger.info("MS CliShell test : {}", shell.execute("hostname").getOutput());
                logger.info("Host ip : {}", MS.getIp());
                if(!MS.getUser().equalsIgnoreCase("root")) {
                    path = "/home/" + MS.getUser();
                } else {
                    path="/root";
                }
                checkEnmOnRack();
            }
            logger.info("path for neo4j files {}", path);
            numberOfAlarms = Integer.parseInt(getProperty("fm.alarms", "5"));
            enmUsername = getProperty("enm.username", "").trim();
            enmPassword = getProperty("enm.password", "").trim();

            fmWaitingTimePerAlarm = Integer.parseInt(getProperty("fm.waiting.time.per.alarm", "15"));
            timeToWaitForServiceOnline = Integer.parseInt(getProperty("time.to.wait.for.service.online", "10"));

            netsimConfig = getNetsimProperties("nodes.conf", "");
            radioNetsimConfig = getNetsimProperties("radio.nodes.conf", "");

            pmTimeToWaitForSubscription = Long.parseLong(getProperty("pm.time.to.wait.for.subscription", "120")); //added 60 for RTD-12377
            cmbeTimeToWaitJobComplete = Long.parseLong(getProperty("cmbe.time.to.wait.for.job.complete", "60"));

            nexusUrl = getProperty("nexus.url", "").trim();
            logger.info("Using nexusUrl : {}", nexusUrl);

            final String hatoolsVersion = getProperty("hatools.version", "").trim();
            final String hatoolsUrlAUX = getProperty("hatools.url", "").trim();
            hatoolsUrl = String.format(hatoolsUrlAUX, nexusUrl, hatoolsVersion, hatoolsVersion);
            logger.info("hatoolsUrl : {}", hatoolsUrl);
            testType = getProperty("test.type", "");

            fillFunctionalAreasMap(); //Check if functional TC is disabled.
            if (isEnvCloudNative()) {
                if (testType.equalsIgnoreCase("Regression")) {
                    fillThresholdMapCloudNative("app.cenm.reg");
                } else {
                    fillThresholdMapCloudNative("app.cenm.ug");
                }
            } else if (isEnvCloud()) {
                if (testType.equalsIgnoreCase("Regression")) {
                    fillThresholdMapCloud("app.cloud.reg");
                } else {
                    fillThresholdMapCloud("app.cloud.ug");
                }
            } else {
                if (testType.equalsIgnoreCase("Regression")) {
                    fillThresholdMapPhysical("app.phy.reg");
                } else {
                    fillThresholdMapPhysical("app.phy.ug");
                    UpgradeUtils.extractEnminstPackage();
                }
            }

            logger.info("functionalAreasThresholdMap : {}", functionalAreasThresholdMap);

            timeToSleep = Integer.parseInt(getProperty("upgrade.sleep", "60"));
            waitForUpgradeToStart = Long.parseLong(getProperty("upgrade.wait.for.start", "480")) * HAconstants.HAtime.ONE_MINUTE_IN_SECONDS * Constants.TEN_EXP_9;
            logger.info("waitForUpgradeToStart = {}", waitForUpgradeToStart);

            runOnce = Boolean.parseBoolean(getProperty("run.once", "false"));

            ropTime = Integer.parseInt(getProperty("rop.time", "0"));

            long httpTimeout = Long.parseLong(getProperty("http.session.timeout", "45"));

            httpSessionTimeout = Constants.TEN_EXP_9 * (long) Constants.Time.ONE_MINUTE_IN_SECONDS * httpTimeout;

            maxDisruptionTime = Long.parseLong(getProperty("max.disruption.time", "600"));

            upgradeStartTime = Long.parseLong(getProperty("upgrade.start.time", "0"));

            timeToWaitNodesToSync = Long.parseLong(getProperty("time.wait.nodes.sync", "3"));

            sfsIlo1Ip = getProperty("sfs.ilo1.ip", "");
            sfsIlo2Ip = getProperty("sfs.ilo2.ip", "");
            sfsIloUsername = getProperty("sfs.ilo.username", "");
            sfsIloPassword = getProperty("sfs.ilo.password", "");
            sfsClusterUsername = getProperty("sfs.cluster.username", "");
            sfsClusterPassword = getProperty("sfs.cluster.password", "");
            sfsClusterIp = getProperty("sfs.cluster.ip", "");

            wlp = getProperty("wl.profiles", "");

            final List<Map<String, String>> dbList = new ArrayList<>();
            if (!envCloud) {
                for (int i = 1; i <= Database.getAllHosts().size(); i++) {
                    dbList.add(Collections.unmodifiableMap(fillHostList("db-" + i)));
                }
            }

            final List<Map<String, String>> svcList = new ArrayList<>();
            if (!envCloud) {
                for (int i = 1; i <= Service.getAllHosts().size(); i++) {
                    svcList.add(Collections.unmodifiableMap(fillHostList("svc-" + i)));
                }
            }
            final HaToolsProperties haToolsProperties = new PropertiesBuilder(
                    // TODO - do we need all those properties on CLOUD ???
                    envCloud ? "" : SVC1.getUser(),
                    envCloud ? "" : SVC1.getPass(),
                    envCloud ? "" : SVC1.getPass(UserType.ADMIN),
                    MS.getIp(),
                    MS.getPass(),
                    envCloud ? "" : DataHandler.getHostByType(HostType.HTTPD).getPass(),
                    envCloud ? new ArrayList<>() : dbList,
                    envCloud ? new ArrayList<>() : svcList
            )
                    .nasconsolePassword(getProperty("nasconsole.password", ""))
                    .sanPassword(getProperty("san.password", ""))
                    .sanUsername(getProperty("san.username", ""))
                    .hatoolsPath(getProperty("hatools.path", ""))
                    .isDeploymentPhysical(envCloud ? true : Boolean.parseBoolean(getProperty("deployment.physical", FALSE)))
                    .gatewayIp(getProperty("host.gateway.ip", ""))
                    .gatewayPassword(getProperty("host.gateway.user.root.pass", ""))
                    .build();

            PropertiesReader.buildPropertiesReader(haToolsProperties);

        } catch (final IOException e) {
            logger.warn("Failed to read taf_properties/hosts.properties", e);
        }

        try (final InputStream ha = Thread.currentThread().getContextClassLoader().getResourceAsStream("ha.properties")) {
            PropertiesReader.properties.load(ha);


            haUsername = getProperty("ha.username", "").trim();
            haPassword = getProperty("ha.password", "").trim();

            appRestTimeOut = Integer.parseInt(getProperty("application.rest.call.timeout", ""));
            pmRestTimeOut = Integer.parseInt(getProperty("pm.rest.call.timeout", ""));
            enmLoginLogoutRestTimeOut = Integer.parseInt(getProperty("enm.login.logout.timeout", ""));
            cloudUgStartTimeLimit = (long)(Float.parseFloat(getProperty("cloud.ug.start.timelimit", "")) * 3600);
            cloudUgFinishTimeLimit = (long)(Float.parseFloat(getProperty("cloud.ug.finish.timelimit", "")) * 3600);
            aduDurationOnDemand = (long)(Float.parseFloat(getProperty("adu.duration.on.demand", "0")) * 3600);
            useExternalVm = Boolean.parseBoolean(getProperty("use.external.vm", "false"));
            externalVmType = getProperty("external.vm.type", "gateway");
            externalVmIP = getProperty("external.vm.ip", "");
            if (envCloud) {
                logger.info("Cloud upgrade start time limit : {} sec.",cloudUgStartTimeLimit);
                logger.info("Cloud upgrade finish time limit : {} sec.", cloudUgFinishTimeLimit);
            }
            if(envCloudNative){
                logger.info("ADU duration in CCD Upgrade is : {} sec.", aduDurationOnDemand);
            }
            logger.info("user name is {} password{} apptimeout{}  pmtimeout{}  enmLoginLogoutRestTimeOut{}", haUsername, haPassword, appRestTimeOut, pmRestTimeOut, enmLoginLogoutRestTimeOut);

        } catch (final IOException e) {
            logger.warn("Failed to read ha.properties", e);
        }

        if (envCloudNative) {
            try {
                //cENM comment. Set additional properties for cENM environment.
                logger.info("Cloud native env!");
                cloudIngressHost = getProperty("cloud.ingress.host", "");
                cEnmName = getProperty("cloud.deployment.enm", "");
                logger.info("Using cloud deployment : {}", cEnmName);
                logger.info("cENM cloudIngressHost : {}", cloudIngressHost);
                logger.info("cENM enmUsername : {}", enmUsername);
                logger.info("cENM enmPassword : {}", enmPassword);
                logger.info("cENM workload : {}", WORKLOAD);

                cEnmChaosTestType = getProperty("cenm.chaos.regression.type", "");
                cEnmChaosAppName = getProperty("cenm.chaos.regression.app", "");
                cEnmChaosMode = getProperty("cenm.chaos.regression.mode", "");
                cEnmChaosFaultDuration = getProperty("cenm.chaos.regression.duration", "NA");
                cEnmChaosContainerName = getProperty("cenm.chaos.regression.container", "NA");
                if (testType.equalsIgnoreCase("Regression")) {
                    logger.info("cENM Chaos-Regression Test type : {}", cEnmChaosTestType);
                    logger.info("cENM Chaos-Regression app name : {}", cEnmChaosAppName);
                    logger.info("cENM Chaos-Regression fault mode : {}", cEnmChaosMode);
                    logger.info("cENM Chaos-Regression fault duration : {}", cEnmChaosFaultDuration);
                    logger.info("cENM Chaos-Regression container name : {}", cEnmChaosContainerName);
                }
                //Create Rest Client
                httpRestServiceClientCloudNative = new HttpRestServiceClient();
                logger.info("useExternalVm : {}", useExternalVm);
                if (useExternalVm) {
                    httpRestServiceExternalClient = new HttpRestServiceClient("EXTERNAL");
                    configureExternalWatcherService();
                } else {
                    httpRestServiceExternalClient = httpRestServiceClientCloudNative;
                }
                CommonUtils.sleep(3);
                setNamespaceOnDeployment(cEnmName);
                getFromPsvFromServer();
                CommonUtils.sleep(1);

                final String[] multiDependencies = getNetsimProperties("multi.dependencies", "").split(";");
                for (String str : multiDependencies) {
                    final String[] depList = str.split(":");
                    final String key = depList[0];
                    final List<String> instances = Arrays.asList(depList[1].split(","));
                    multiInstanceMap.put(key, instances);
                }
                logger.info("multiInstanceMap : {}", multiInstanceMap);
            } catch (Exception e) {
                logger.warn("Message : {}", e.getMessage());
            }
        }
        if (!envCloudNative) {
            try {
                if (HAPropertiesReader.isEnvCloud()) {
                    CloudDependencyDowntimeHelper.generatingPemKey();
                }
                getNeo4jVersion();
            }
            catch (final Exception e){
                logger.info("exception in fetching neo4j version {}",e.getMessage());
            }
        }
    }

    public static Host getExternalUserHost(final String hostName) {
        logger.info("User external Host : {}", hostName);
        Host extHost = new Host();
        extHost.setHostname(hostName);
        extHost.setIp(hostName);
        extHost.setUser("root");
        extHost.setPass("shroot");
        extHost.setType(HostType.GATEWAY);
        Map<Ports, String> port = new HashMap<>();
        port.put(Ports.SSH, "22");
        extHost.setPort(port);
        return extHost;
    }

    private static Map<String, Long> loadThresholdValues(final String thresholdType, final String prefix) {
        final Map<String, Long> thresholdMap = new HashMap<>();
        final Properties props = new Properties();
        final String fileName = thresholdType.equalsIgnoreCase("app") ? "app.properties" : "infra.properties";
        try {
            final InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("threshold/" + fileName);
            props.load(in);
            for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                String value = props.getProperty(name);
                if (name.startsWith(prefix)) {
                    String component = name.substring(name.lastIndexOf(".") + 1);
                    logger.info("component : {}, threshold value : {}", component, value);
                    thresholdMap.put(component, Long.parseLong(value));
                }
            }
        } catch (final Exception e) {
            logger.warn("Failed to read Thresholds : {}", e.getMessage());
        }
        logger.info("{} thresholdMap : {}", prefix, thresholdMap);
        return thresholdMap;
    }

    public static boolean isHaltHostHardResetRegression() {
        final boolean isHaltHostHardResetRegression = (getTestType().equalsIgnoreCase("Regression") &&
                (getTestSuitName().contains("HardResetHost") || getTestSuitName().contains("HaltHost")));
        logger.info("isHaltHostHardResetRegression : {}", isHaltHostHardResetRegression);
        return isHaltHostHardResetRegression;
    }

    private static boolean isHaltHostHardResetRegressionSVC() {
        boolean isSvc = false;
        final boolean isHaltHostHardResetRegression = (getTestType().equalsIgnoreCase("Regression") &&
                (getTestSuitName().contains("HardResetHost") || getTestSuitName().contains("HaltHost")));
        logger.info("isHaltHostHardResetRegression : {}", isHaltHostHardResetRegression);
        if (isHaltHostHardResetRegression) {
            final Object host = DataHandler.getAttribute("host.name");
            isSvc = StringUtils.containsIgnoreCase(host.toString(), "SVC");
            logger.info("Host: {}, isSvc: {}", host.toString(), isSvc);
        }
        return (isHaltHostHardResetRegression && isSvc);
    }

    private static void configureExternalWatcherService() {
        try {
            final CliShell shell = new CliShell(getExternalHost());
            shell.execute("rm -rf /usr/local/adu;mkdir -p /usr/local/adu");
            final InputStream fileToCopyStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("scripts/files.properties");
            final Scanner reader = new Scanner(fileToCopyStream);
            while (reader.hasNext()) {
                final String fileName = reader.nextLine();
                logger.info("filename is : {}", fileName);
                final String resourceName = fileName.equalsIgnoreCase("watcher.war") ? "watcher.war" : ("scripts/" + fileName);
                final InputStream fileStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
                final File resourceFile = new File("/var/tmp/" + fileName);
                if (fileStream != null) {
                    FileUtils.copyInputStreamToFile(fileStream, resourceFile);
                    CommonUtils.uploadFileOnHost(HAPropertiesReader.getExternalHost(), "/usr/local/adu", resourceFile);
                    logger.info("ResourceFile {} delete status: {}", fileName, resourceFile.delete());
                } else {
                    logger.error("Resource {} copy failed : resource is null !", fileName);
                }
            }
            final String clusterId = HAPropertiesReader.getProperty("taf.config.dit.deployment.name", "");
            final String hostType = externalVmType;
            logger.info("Using cluster id : {}", clusterId);
            logger.info("Using host type : {}", hostType);
            final CliResult result = shell.execute("find /usr/local/adu/ -type f -name \"*\" | grep -v watcher.war | xargs sed -i \"s/\\r//g\";chmod +x /usr/local/adu/config.sh;/usr/local/adu/config.sh " + clusterId + " " + hostType);
            logger.info("Result : \n{}", result.getOutput());
            if (result.getExitCode() == 0) {
                logger.info("watcher web service configured successfully.");
            }
        } catch (final Exception e) {
            logger.error("ConfigureWatcherService failed: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * cENM comment.
     * Set namespace in adu-watcher pod for upgrade/endpoint watcher.
     * @param cEnmName
     */
    private static void setNamespaceOnDeployment(final String cEnmName) {
        try {
            final String uri = "watcher/adu/namespace/" + cEnmName;
            final HttpResponse httpResponse = httpRestServiceExternalClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
            logger.info("Set deployment namespace responseCode : {}", httpResponse.getResponseCode().getCode());
            if (! httpResponse.getResponseCode().equals(HttpStatus.findByCode(204))) {
                isWatcherConnectionFailed = true;
            }
        } catch (Exception e) {
            logger.warn("Failed to set deployment namespace, Error Message : {}", e.getMessage());
            isWatcherConnectionFailed = true;
        }
    }

    private static void getFromPsvFromServer() {
        try {
            final String uri = "watcher/adu/psv";
            final HttpResponse httpResponse = httpRestServiceExternalClient.sendGetRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, uri);
            logger.info("Get From PSV responseCode : {}", httpResponse.getResponseCode().getCode());
            String psv = httpResponse.getBody().trim();
            if(httpResponse.getResponseCode().equals(HttpStatus.OK) && !psv.isEmpty()) {
                FROM_ISO = psv;
                logger.info("FROM_ISO : {}", FROM_ISO);
            }
        } catch (Exception e) {
            logger.warn("Failed to get PSV from watcher, Error Message : {}", e.getMessage());
        }
    }

    public static String getNexusUrl() {
        return nexusUrl;
    }

    public static String getTestSuitName() {
        final Object suitName = DataHandler.getAttribute("regression.testsuite.name");
        if (suitName != null && !suitName.toString().isEmpty()) {
            return suitName.toString();
        }
        return "";
    }

    public static String getEnminstWgetCmd() {
        final String enminstVersion = getProperty("enminst.version", "3.0.97");
        return String.format(getProperty("enminst.wget.rpm", ""), nexusUrl, enminstVersion, enminstVersion);
    }

    public static String getEnminstExtractCmd() {
        final String enminstVersion = getProperty("enminst.version", "3.0.97");
        return String.format(getProperty("enminst.extract.cmd", ""), enminstVersion);
    }

    public static String getEnminstReleaseUrl() {
        return getProperty("enminst.release.url", "");
    }

    public static String getSvcDbIloIP(final String hostName) {
        final String iloIpFromJob = getProperty("regression.ilo.ip", "");
        if (!iloIpFromJob.isEmpty()) {
            logger.info("iloIpFromJob : {}", iloIpFromJob);
            return iloIpFromJob.trim();
        }
        String hostIp = "";
        switch (hostName) {
            case "svc1":
                hostIp = getProperty("svc-1.ilo.ip", "");
                break;
            case "svc2":
                hostIp = getProperty("svc-2.ilo.ip", "");
                break;
            case "svc3":
                hostIp = getProperty("svc-3.ilo.ip", "");
                break;
            case "svc4":
                hostIp = getProperty("svc-4.ilo.ip", "");
                break;
            case "db1":
                hostIp = getProperty("db-1.ilo.ip", "");
                break;
            case "db2":
                hostIp = getProperty("db-2.ilo.ip", "");
                break;
            default:
                logger.warn("Invalid host : {}", hostName);
                break;
        }
        return hostIp;
    }

    public  static boolean isAmosOnSVC() {
        if(!isEnvCloud()) {
            try {
                final String command = "grep amos /etc/hosts";
                final CliShell shell = new CliShell(MS);
                logger.info("Amos on svc command is : {}", command);
                CliResult cliResult = shell.executeAsRoot(command);
                logger.info("Amos command output : {}", cliResult);
                if (cliResult.isSuccess()) {
                    final String result = cliResult.toString().toLowerCase();
                    if (result.contains("svc-") && result.contains("-amos")) {
                        return true;
                    }
                } else {
                    logger.warn("failed to run command : {}", command);
                }
            } catch (Exception e) {
                logger.warn("Failed to run Amos command, message : {}", e.getMessage());
            }
        } else {
            logger.info("env is Cloud. don't check for Amos on svc.");
        }
        return false;
    }

    public static String getIloRootPassword() {
        return getProperty("host.ilo.root.password", "shroot12");
    }

    public static Host getWorkload() {
        return WORKLOAD;
    }

    public static HttpRestServiceClient getHttpRestServiceClientCloudNative() {
        return httpRestServiceClientCloudNative;
    }

    public static Host getExternalHost() {
        if (externalVmType.equalsIgnoreCase("workload")) {
            return WORKLOAD;
        } else if (externalVmType.equalsIgnoreCase("gateway")) {
            final Host gateway = DataHandler.getHostByType(HostType.GATEWAY);
            logger.info("gateway ip : {}", gateway.getIp());
            return gateway;
        } else if (externalVmType.equalsIgnoreCase("user-vm")) {
            if (externalVmIP.isEmpty()) {
                logger.error("Failed to get user externalVmIP Address .. using default gateway.");
            } else {
                logger.info("User external-vm ip : {}", externalVmIP);
                return getExternalUserHost(externalVmIP);
            }
        }
        return DataHandler.getHostByType(HostType.GATEWAY);
    }

    public static HttpRestServiceClient getHttpRestServiceExternalClient() {
        return httpRestServiceExternalClient;
    }

    public static boolean isExternalVmUsed() {
        return useExternalVm;
    }

    public static boolean isEnvCloudNative() {
        return envCloudNative;
    }

    public static boolean isDeploymentCCD () {
        return (aduDurationOnDemand > 0);
    }

    public static String getFromIso() {
        return FROM_ISO;
    }

    public static String getToIso() {
        return TO_ISO;
    }

    public static String getTafClusterId(){
        return tafClusterId;
    }

    public static String getTafConfigDitDeploymentName() {
        return tafConfigDitDeploymentName;
    }

    public static boolean isEnvCloud() {
        return envCloud;
    }

    public static boolean isEnmOnRack() {
        return isEnmOnRack;
    }

    public static String getSupportUserPassword() {
        return getProperty("host.support.password", "symantec");
    }

    private static void fillFunctionalAreasMap() {
        functionalAreasMap.put(AMOS, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, AMOS), FALSE)));
        functionalAreasMap.put(APPLAUNCH, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, APPLAUNCH), FALSE)));
        functionalAreasMap.put(CMBE, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, CMBE), FALSE)));
        functionalAreasMap.put(CMBIL, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, CMBIL), FALSE)));
        functionalAreasMap.put(CM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, CM), FALSE)));
        functionalAreasMap.put(ESM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, ESM), FALSE)));
        functionalAreasMap.put(FM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, FM), FALSE)));
        functionalAreasMap.put(FMR, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, FMR), FALSE)));
        functionalAreasMap.put(FMB, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, FMB), FALSE)));
        functionalAreasMap.put(PMR, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, PMR), FALSE)));
        functionalAreasMap.put(NBIVA, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, NBIVA), FALSE)));
        functionalAreasMap.put(NBIVS, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, NBIVS), FALSE)));
        functionalAreasMap.put(NETEX, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, NETEX), FALSE)));
        functionalAreasMap.put(PM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, PM), FALSE)));
        functionalAreasMap.put(SHM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, SHM), FALSE)));
        functionalAreasMap.put(UM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, UM), FALSE)));
        functionalAreasMap.put(SYSTEM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, SYSTEM), FALSE)));
        functionalAreasMap.put(AALDAP, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, AALDAP), FALSE)));
        functionalAreasMap.put(CESM, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, CESM), FALSE)));
        functionalAreasMap.put(FAN, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, FAN), FALSE)));
        functionalAreasMap.put(SMRS, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, SMRS), FALSE)));
        functionalAreasMap.put(FLS, Boolean.parseBoolean(getProperty(String.format(FUNCTIONAL_AREA, FLS), FALSE)));
    }

    public static void disableFunctionalAreasRequireNodes() {
        functionalAreasMap.put(AMOS, false);
        functionalAreasMap.put(CMBE, false);
        functionalAreasMap.put(CMBIL, false);
        functionalAreasMap.put(CM, false);
        functionalAreasMap.put(ESM, false);
        functionalAreasMap.put(FM, false);
        functionalAreasMap.put(FMR, false);
        functionalAreasMap.put(FMB, false);
        functionalAreasMap.put(PMR, false);
        functionalAreasMap.put(NBIVA, false);
        functionalAreasMap.put(NBIVS, false);
        functionalAreasMap.put(NETEX, false);
        functionalAreasMap.put(PM, false);
        functionalAreasMap.put(SHM, false);
        functionalAreasMap.put(SMRS, false);
        logger.info("functionalAreasMap : {}", functionalAreasMap);
    }

    private static void fillThresholdMapPhysical(final String prefix) {
        final Map<String, Long> appThreshold = loadThresholdValues("app", prefix);
        fillThresholdByApp(appThreshold.get(AMOS), Amos.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(APPLAUNCH), AppLauncher.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CM), ConfigurationManagement.class.getSimpleName());
        fillThresholdByApp(isHaltHostHardResetRegressionSVC() ? 360L : appThreshold.get(CMBE), CmBulkExport.class.getSimpleName());
        fillThresholdByApp(isHaltHostHardResetRegressionSVC() ? 360L : appThreshold.get(CMBIL), CmImportToLive.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(ESM), EnmSystemMonitor.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FM), FaultManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FMR), FaultManagementRouter.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FMB), FaultManagementBsc.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(PMR), PerformanceManagementRouter.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NBIVA), NbiAlarmVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NBIVS), NbiCreateSubsVerifier.class.getSimpleName());
        fillThresholdByApp(isHaltHostHardResetRegression() ? 370L : appThreshold.get(NETEX), NetworkExplorer.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(PM), PerformanceManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SHM), SoftwareHardwareManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SYS), SystemVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(UM), UserManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SMRS), ShmSmrs.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FLS), FileLookupService.class.getSimpleName());
    }

    private static void fillThresholdMapCloud(final String prefix) {
        final Map<String, Long> appThreshold = loadThresholdValues("app", prefix);
        fillThresholdByApp(appThreshold.get(AMOS), Amos.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(APPLAUNCH), AppLauncher.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CM), ConfigurationManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CMBE), CmBulkExport.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CMBIL), CmImportToLive.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(ESM),EnmSystemMonitor.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FM), FaultManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FMR), FaultManagementRouter.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FMB), FaultManagementBsc.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(PMR), PerformanceManagementRouter.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NBIVA),NbiAlarmVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NBIVS), NbiCreateSubsVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NETEX), NetworkExplorer.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(PM), PerformanceManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SHM), SoftwareHardwareManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SYS), SystemVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(UM), UserManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SMRS), ShmSmrs.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FLS), FileLookupService.class.getSimpleName());
    }

    private static void fillThresholdMapCloudNative(final String prefix) {
        final Map<String, Long> appThreshold = loadThresholdValues("app", prefix);
        fillThresholdByApp(appThreshold.get(AMOS), Amos.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(APPLAUNCH), AppLauncher.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CM), ConfigurationManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CMBE), CmBulkExport.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CMBIL), CmImportToLive.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FM), FaultManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FMR), FaultManagementRouter.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FMB), FaultManagementBsc.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(PMR), PerformanceManagementRouter.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NBIVA), NbiAlarmVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NBIVS), NbiCreateSubsVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(NETEX), NetworkExplorer.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(PM), PerformanceManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SHM), SoftwareHardwareManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SYS), SystemVerifier.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(UM), UserManagement.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(AALDAP), ComAaLdap.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(CESM), CnomSystemMonitor.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FAN), FileAccessNBI.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(SMRS), ShmSmrs.class.getSimpleName());
        fillThresholdByApp(appThreshold.get(FLS), FileLookupService.class.getSimpleName());
    }

    public static void fillThresholdByApp(final Long deltaThreshold, final String classSimpleName) {
        final Map<String, Long> valuesThresholdMap = new HashMap<>();
        valuesThresholdMap.put(DELTA, deltaThreshold);
        functionalAreasThresholdMap.put(classSimpleName, valuesThresholdMap);
    }

    public static void initAppMap() {
        appMap.put(FunctionalArea.AMOS.get(), AMOSAPP);
        appMap.put(FunctionalArea.CM.get(), CONFIGURATIONMANGEMENT);
        appMap.put(FunctionalArea.PM.get(), PERFORMANCEMANAGEMENT);
        appMap.put(FunctionalArea.FM.get(), FAULTMANAGEMENT);
        appMap.put(FunctionalArea.FMR.get(), FAULTMANAGEMENTROUTER);
        appMap.put(FunctionalArea.FMB.get(), FAULTMANAGEMENTBSC);
        appMap.put(FunctionalArea.PMR.get(), PERFORMANCEMANAGEMENTROUTER);
        appMap.put(FunctionalArea.SHM.get(), SOFTWAREHARDWAREMANAGEMENT);
        appMap.put(FunctionalArea.NBIVA.get(), NBIALARMVERIFIER);
        appMap.put(FunctionalArea.NBIVS.get(), NBICREATESUBSVERIFIER);
        appMap.put(FunctionalArea.CMBE.get(), CMBULKEXPORT);
        appMap.put(FunctionalArea.CMBIL.get(), CMBULKIMPORTTOLIVE);
        appMap.put(FunctionalArea.APPLAUNCH.get(), APPLAUNCHER);
        appMap.put(FunctionalArea.UM.get(), USERMANAGEMENT);
        appMap.put(FunctionalArea.NETEX.get(), NETWORKEXPLORER);
        appMap.put(FunctionalArea.SYSTEM.get(), SYSTEMVERIFIER);
        appMap.put(FunctionalArea.SMRS.get(), SHMSMRS);
        appMap.put(FunctionalArea.FLS.get(), FILELOOKUPSERVICE);
        if (isEnvCloudNative()) {
            appMap.put(FunctionalArea.AALDAP.get(), COMAALDAP);
            appMap.put(FunctionalArea.CESM.get(), CNOMSYSTEMMONITOR);
            appMap.put(FunctionalArea.FAN.get(), FILEACCESSNBI);
        } else {
            appMap.put(FunctionalArea.ESM.get(), ENMSYSTEMMONITOR);
        }
    }

    public static void initFunctionalAreaMap() {
        areaMap.put(FunctionalArea.AMOS, AMOSAPP);
        areaMap.put(FunctionalArea.CM, CONFIGURATIONMANGEMENT);
        areaMap.put(FunctionalArea.PM, PERFORMANCEMANAGEMENT);
        areaMap.put(FunctionalArea.FM, FAULTMANAGEMENT);
        areaMap.put(FunctionalArea.FMR, FAULTMANAGEMENTROUTER);
        areaMap.put(FunctionalArea.FMB, FAULTMANAGEMENTBSC);
        areaMap.put(FunctionalArea.PMR, PERFORMANCEMANAGEMENTROUTER);
        areaMap.put(FunctionalArea.SHM, SOFTWAREHARDWAREMANAGEMENT);
        areaMap.put(FunctionalArea.NBIVA, NBIALARMVERIFIER);
        areaMap.put(FunctionalArea.NBIVS, NBICREATESUBSVERIFIER);
        areaMap.put(FunctionalArea.CMBE, CMBULKEXPORT);
        areaMap.put(FunctionalArea.CMBIL, CMBULKIMPORTTOLIVE);
        areaMap.put(FunctionalArea.APPLAUNCH, APPLAUNCHER);
        areaMap.put(FunctionalArea.UM, USERMANAGEMENT);
        areaMap.put(FunctionalArea.NETEX, NETWORKEXPLORER);
        areaMap.put(FunctionalArea.SYSTEM, SYSTEMVERIFIER);
        areaMap.put(FunctionalArea.SMRS, SHMSMRS);
        areaMap.put(FunctionalArea.FLS, FILELOOKUPSERVICE);
        if (isEnvCloudNative()) {
            areaMap.put(FunctionalArea.AALDAP, COMAALDAP);
            areaMap.put(FunctionalArea.CESM, CNOMSYSTEMMONITOR);
            areaMap.put(FunctionalArea.FAN, FILEACCESSNBI);
        } else {
            areaMap.put(FunctionalArea.ESM, ENMSYSTEMMONITOR);
        }
    }

    public static void initCleanupMap() {
        cleanupMap.put(FunctionalArea.PM, TestCase.PM_CLEANUP);
        cleanupMap.put(FunctionalArea.FM, TestCase.FM_CLEANUP);
        cleanupMap.put(FunctionalArea.FMR, TestCase.FMR_CLEANUP);
        cleanupMap.put(FunctionalArea.FMB, TestCase.FMB_CLEANUP);
        cleanupMap.put(FunctionalArea.PMR, TestCase.PMR_CLEANUP);
        cleanupMap.put(FunctionalArea.SHM, TestCase.SHM_CLEANUP);
        cleanupMap.put(FunctionalArea.NBIVA, TestCase.NBIVA_CLEANUP);
        cleanupMap.put(FunctionalArea.NBIVS, TestCase.NBIVS_CLEANUP);
        cleanupMap.put(FunctionalArea.CMBE, TestCase.CMBE_CLEANUP);
        cleanupMap.put(FunctionalArea.CMBIL, TestCase.CMBIL_CLEANUP);
        cleanupMap.put(FunctionalArea.UM, TestCase.UM_CLEANUP);
        cleanupMap.put(FunctionalArea.NETEX, TestCase.NETEX_CLEANUP);
        cleanupMap.put(FunctionalArea.SMRS, TestCase.SMRS_CLEANUP);
        if (isEnvCloudNative()) {
            cleanupMap.put(FunctionalArea.AALDAP, TestCase.AALDAP_CLEANUP);
            cleanupMap.put(FunctionalArea.FAN, TestCase.FAN_CLEANUP);
        }
    }

    public static boolean isNodeOperationRequired() {
        if (functionalAreasMap.get(CM) ||
                functionalAreasMap.get(PM) ||
                functionalAreasMap.get(FM) ||
                functionalAreasMap.get(FMR) ||
                functionalAreasMap.get(FMB) ||
                functionalAreasMap.get(PMR) ||
                functionalAreasMap.get(SHM) ||
                functionalAreasMap.get(AMOS) ||
                functionalAreasMap.get(NBIVA) ||
                functionalAreasMap.get(NBIVS) ||
                functionalAreasMap.get(CMBE) ||
                functionalAreasMap.get(CMBIL) ||
                functionalAreasMap.get(SMRS) ||
                functionalAreasMap.get(NETEX)) {
            return true;
        }
        return false;
    }

    public static void printTestwareProperties() {
        if (!testType.equalsIgnoreCase("Regression")) {
            if (isEnvCloudNative()) {
                if (isISOSmallerThan(new int[]{24, 2, 9, 0}, FROM_ISO, SMRS) && isTerminationGracePeriodSecondsSmallerThan(60)) {
                    logger.info("disabling SMRS as PS is lower than: 24.02.9");
                    functionalAreasMap.put(SMRS, false);
                }
                if (isISOSmallerThan(new int[]{24, 2, 45, 4}, FROM_ISO, FMB)) {
                    logger.info("disabling FMB as PS is lower than: 24.02.45-4");
                    functionalAreasMap.put(FMB, false);
                }
                getEsmPodName();
                if (isISOSmallerThan(new int[]{23, 1, 56, 1}, FROM_ISO, CESM) || !isEsmPodPresentOnServer) {
                    logger.info("disabling CESM/CNOM as PS is lower than: 23.01.56-1");
                    functionalAreasMap.put(CESM, false);
                    disableCnomUsecase = true;
                }
                logger.info("Disbale cnom value is {}", disableCnomUsecase);
            } else if (isEnvCloud()) {
                if (isISOSmallerThan(new int[]{2, 31, 84, 0}, FROM_ISO, SMRS)) {
                    logger.info("disabling SMRS as ISO is lower than: 2.31.84");
                    functionalAreasMap.put(SMRS, false);
                }
                if (isISOSmallerThan(new int[]{2, 19, 52, 0}, FROM_ISO, ESM)) {
                    logger.info("disabling ESM as ISO is lower than: 2.19.52");
                    functionalAreasMap.put(ESM, false);
                }
            } else {
                if (isEnmOnRack()) {
                    logger.info("disabling SMRS as use case is not supported on Rack server");
                    functionalAreasMap.put(SMRS, false);
                } else if (isISOSmallerThan(new int[]{2, 26, 94, 0}, FROM_ISO, SMRS)) {
                    logger.info("disabling SMRS as ISO is lower than: 2.26.94");
                    functionalAreasMap.put(SMRS, false);
                }
                if (isISOSmallerThan(new int[]{2, 19, 52, 0}, FROM_ISO, ESM)) {
                    logger.info("disabling ESM as ISO is lower than: 2.19.52");
                    functionalAreasMap.put(ESM, false);
                }
                if (!isISOSmallerThan(new int[]{2, 41, 103, 0}, TO_ISO, FTS)) {
                    ftsOffDateCmd = "sudo /bin/egrep -h 'Generating.public.private.rsa.key.pair' /var/log/messages | /bin/cut -b -15 | tail -n 1";
                }
            }
        }
        logger.info("---------- TOR TESTWARE PROPERTIES ---------------");
        logger.info("ms.ip\t=\t{}", HAPropertiesReader.getMsIp());
        new TreeMap<>(functionalAreasMap).forEach((s, aBoolean) -> logger.info("functional.area." + s + "\t=\t{}", aBoolean));
        final Object tafClusterID = DataHandler.getAttribute("taf.clusterId");
        if (tafClusterID != null) {
            final String substring = tafClusterID.toString().substring(0, 3);
            logger.info("taf.clusterId\t=\t{}", substring);
        }
        final Object tafConfigDitDeploymentName = DataHandler.getAttribute("taf.config.dit.deployment.name");
        if (tafConfigDitDeploymentName != null) {
            logger.info("taf.config.dit.deployment.name\t=\t{}", tafConfigDitDeploymentName);
        }
        logger.info("taf.gateway\t=\t{}", DataHandler.getAttribute("host.gateway.ip"));
        logger.info("---------- TOR TESTWARE PROPERTIES ---------------");
    }

    private static void checkEnmOnRack() {
        try {
            final String checkEnmOnRackCommand = "cat \"/software/$(ls -1 /software | grep 'siteEngineering' | sort -r -n -k1 | /usr/bin/head -1)\" | grep -i 'environment_model'";
            logger.info("checkEnmOnRackCommand is: {}", checkEnmOnRackCommand);
            CliShell msShell = new CliShell(HAPropertiesReader.getMS());
            CliResult result = msShell.execute(checkEnmOnRackCommand);
            logger.info("checkEnmOnRackCommand output is: {}", result.getOutput());
            if (result.isSuccess()) {
                if (StringUtils.containsIgnoreCase(result.getOutput(), "ENMOnRack")) {
                    isEnmOnRack = true;
                }
            } else {
                logger.info("Failed to execute the checkEnmOnRackCommand.");
            }
        } catch (Exception ex) {
            logger.warn("Failed to execute checkEnmOnRackCommand: {}", ex.getMessage());
        }
        logger.info("isEnmOnRack: {}", isEnmOnRack());
    }

    /**
     * Returns true if from iso/psv is less than target iso/psv.
     *
     * @return boolean
     */
    private static boolean isISOSmallerThan(int[] targetISO, String ISO, String app) {
        try {
            logger.info("comparing ISO = {} and targetISO = {}.{}.{}-{} for app = {}", ISO, targetISO[0], targetISO[1], targetISO[2], targetISO[3], app);
            if (!(ISO.equals("-") || ISO.equals("NA") || ISO.isEmpty())) {
                final String[] iso = ISO.replace(".", ",").split(",");
                final String[] fromISO = new String[] {iso[0], iso[1], iso[2], "0"};
                if (iso[2].contains("-")) {
                    fromISO[2] = iso[2].split("-")[0];
                    fromISO[3] = iso[2].split("-")[1];
                }
                if(Integer.parseInt(fromISO[0]) > targetISO[0]) {
                    logger.info("ISO version is greater than target ISO");
                    return false;
                } else if(Integer.parseInt(fromISO[0]) == targetISO[0]) {
                    if(Integer.parseInt(fromISO[1]) > targetISO[1]) {
                        logger.info("ISO version is greater than target ISO");
                        return false;
                    } else if(Integer.parseInt(fromISO[1]) == targetISO[1]) {
                        if(Integer.parseInt(fromISO[2]) > targetISO[2]) {
                            logger.info("ISO version is greater than target ISO");
                            return false;
                        } else if(Integer.parseInt(fromISO[2]) == targetISO[2]) {
                            if(Integer.parseInt(fromISO[3]) > targetISO[3]) {
                                logger.info("ISO version is greater than target ISO");
                                return false;
                            } else if(Integer.parseInt(fromISO[3]) == targetISO[3]) {
                                logger.info("ISO version is equal to target ISO");
                                return false;
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            logger.info("Exception in parsing iso version for {} : {}", app, e.getMessage());
        }
        logger.info("ISO version is less than target ISO");
        return true;
    }

    private static boolean isTerminationGracePeriodSecondsSmallerThan(int minValue) {
        final String uri = "watcher/adu/fts/graceperiod";
        String output = "";
        int gracePeriod = -1;
        try {
            HttpResponse response = CloudNativeDependencyDowntimeHelper.generateHttpGetResponse(uri);
            output = response.getBody().trim();
            gracePeriod = Integer.parseInt(output);
        } catch (Exception e) {
            logger.info("failed to get terminationGracePeriodSeconds for fts: {}", e.getMessage());
        }
        logger.info("TerminationGracePeriodSeconds output: {}", output);
        return gracePeriod < minValue;
    }

    private static void getEsmPodName() {
        String podName;
        final HttpResponse esmPod = CloudNativeDependencyDowntimeHelper.generateHttpGetResponse("watcher/adu/esm");
        if (esmPod != null && HttpStatus.OK.equals(esmPod.getResponseCode())) {
            final Scanner reader = new Scanner(esmPod.getContent());
            if (reader.hasNext()) {
                podName = reader.nextLine();
                logger.info("podName is {}", podName);
                if (podName.contains("eric-esm-server")) {
                    isEsmPodPresentOnServer = true;
                }
            }
        } else {
            logger.warn("unable to fetch esm pod name.");
        }
        logger.info("isEsmPodPresentOnServer value is {}", isEsmPodPresentOnServer);
    }

    public static void setNeoConfiguration() {
        if (!HAPropertiesReader.isEnvCloud()) {
            try {
                logger.info("Checking Neo4j config.");
                final CliShell shell = new CliShell(MS);
                final CliResult neoResult = shell.executeAsRoot("/opt/ericsson/enminst/bin/vcs.bsh --groups | grep neo4j");

                if (neoResult.getExitCode() == 0 && neoResult.getOutput().contains("ONLINE")) {
                    String neoconfigFlag = checkNeoConfig();
                    if (neoconfigFlag.equalsIgnoreCase("SINGLE")) {
                        neoconfig40KFlag = true;
                        logger.info("Successfully executed neo4j command {} and neoconfig40KFlag value is {}", neoResult.getOutput(), neoconfig40KFlag);
                    } else if (neoconfigFlag.equalsIgnoreCase("CORE")) {
                        neoconfig60KFlag = true;
                        logger.info("Successfully executed neo4j command {} and neoconfig60KFlag value is {}", neoResult.getOutput(), neoconfig60KFlag);
                    }
                } else {
                    logger.error("Neo command failed, result: {}", neoResult.getOutput());
                }
            } catch (final Exception e) {
                logger.info("Failed in getting Neo configuration details : {}", e.getMessage());
            }
        }
    }

    private static String checkNeoConfig() {
        String coreValue = "";
        if (!RegressionUtils.getDbList().isEmpty()) {
            logger.debug("Getting Neo4j configuration from Env");
            CliShell shell = new CliShell(MS);
            coreValue = getNeo4jConfiguration(shell);
        }
        return coreValue;
    }

    public static String getNeo4jConfiguration(final CliShell shell) {
        String neoType = "";
        final String command = "cat /etc/hosts | grep db-";
        final CliResult result = shell.executeAsRoot(command);
        final String resultString = result.getOutput();
        int numberOfDBs = 0;
        logger.info("resultString : {}", resultString);
        if (result.getExitCode() == 0) {
            final String[] resultArray = resultString.split("\n");
            for (final String str : resultArray) {
                if (str.contains("db-1") || str.contains("db-2") || str.contains("db-3") || str.contains("db-4")) {
                    numberOfDBs = numberOfDBs + 1 ;
                }
            }
            logger.info("Number of DBs present in environment : {}", numberOfDBs);
            if (numberOfDBs <= 2) {
                neoType = "SINGLE";
            } else {
                neoType = "CORE";
            }
        } else {
            logger.warn("Neo4j Configuration Command output is : {}", result.getOutput());
        }
        logger.info("neoType : {}", neoType);
        return neoType;
    }

    private static void getNeo4jVersion() {
        final String aliveMemberIpCommand = "consul members | grep neo4j | grep alive | /bin/tr -s ' ' | head -n 1";
        final String versionCmd = "source /ericsson/3pp/neo4j/conf/neo4j_env;/ericsson/3pp/neo4j/bin/neo4j --version";
        final String cloudVersionCmd = "ssh -q -o StrictHostKeyChecking=no -i /var/tmp/Bravo/pemKey.pem cloud-user@%s 'source /ericsson/3pp/neo4j/conf/neo4j_env;sudo /ericsson/3pp/neo4j/bin/neo4j --version -u neo4j -p Neo4jadmin123'";
        final CliResult version;
        String neo4jVer = StringUtils.EMPTY;
        try {
            if (isEnvCloud()) {
                final CliShell shell = new CliShell(MS);
                final CliResult memberIpResult;
                String memberVmIp = "";
                memberIpResult = shell.execute(String.format(aliveMemberIpCommand, "neo4j"));
                logger.info("Get memberIpResult : {}", memberIpResult.getOutput());
                if (memberIpResult.isSuccess()) {
                    memberVmIp = memberIpResult.getOutput().split(" ")[1].split(":")[0];
                }
                if (!memberVmIp.equalsIgnoreCase("")) {
                    version = shell.execute(String.format(cloudVersionCmd, memberVmIp));
                    if (version.isSuccess()) {
                        logger.info("version result {}", version.getOutput()); // Ex:- neo4j 3.5.15
                        int start = version.getOutput().lastIndexOf("neo4j ");
                        if (start != -1) {
                            neo4jVer = version.getOutput().substring(start + 6);
                        }
                    }
                }
            } else {
                final CliShell shell = new CliShell(HostConfigurator.getDb1());
                version = shell.executeAsRoot(versionCmd);
                if (version.isSuccess()) {
                    logger.info("version result {}", version.getOutput());
                    int start = version.getOutput().lastIndexOf("neo4j ");
                    if (start != -1) {
                        neo4jVer = version.getOutput().substring(start + 6);
                    }
                }
            }
            neo4jVersion = versionCompare(neo4jVer); // 0-equals, 1-greater, 2-less, -1 -- something goes wrong
            logger.info("neo4jVersion {}", neo4jVersion);
            if(neo4jVersion == -1){
                logger.error("invalid version");
            }
        } catch (final Exception e) {
            logger.error("Exception in fetching neo4j version {}", e.getMessage());
        }

    }

    public static int versionCompare(String v1) {
        String v2 = "4.0.3";
        int v1Len = org.apache.commons.lang.StringUtils.countMatches(v1, ".");
        int v2Len = org.apache.commons.lang.StringUtils.countMatches(v2, ".");

        if (v1Len != v2Len) {
            int count = Math.abs(v1Len - v2Len);
            if (v1Len > v2Len)
                for (int i = 1; i <= count; i++)
                    v2 += ".0";
            else
                for (int i = 1; i <= count; i++)
                    v1 += ".0";
        }


        if (v1.equals(v2)) {
            return 0;
        }

        String[] v1Str = org.apache.commons.lang.StringUtils.split(v1, ".");
        String[] v2Str = org.apache.commons.lang.StringUtils.split(v2, ".");
        for (int i = 0; i < v1Str.length; i++) {
            String str1 = "", str2 = "";
            for (char c : v1Str[i].toCharArray()) {
                if (Character.isLetter(c)) {
                    return -1;
                } else {
                    str1 += String.valueOf(c);
                }
            }
            for (char c : v2Str[i].toCharArray()) {
                if (Character.isLetter(c)) {
                    return -1;
                } else {
                    str2 += String.valueOf(c);
                }
            }
            v1Str[i] = "1" + str1;
            v2Str[i] = "1" + str2;

            int ver1 = Integer.parseInt(v1Str[i]);
            int ver2 = Integer.parseInt(v2Str[i]);

            if (ver1 != ver2) {
                if (ver1 > ver2) {
                    return 1;
                } else {
                    return 2;
                }
            }
        }
        return -1;
    }

    private static String getJsonImageResource(final String resourceType) {
        final Resource resource;
        if (resourceType.equalsIgnoreCase("app")) {
            resource = Resource.DELTA_DT_CLOUD_NATIVE_APP_IMAGE;
        } else {
            resource = Resource.DELTA_DT_CLOUD_NATIVE_INFRA_IMAGE;
        }
        return resource.getJson();
    }

    private static String getJsonResorce() {
        final Resource resource;
        if (isEnvCloudNative()) {
            resource = Resource.DELTA_DT_CLOUD_NATIVE;
        } else if (neoconfig60KFlag) {
            resource = isEnvCloud() ? Resource.DELTA_DT_CLOUD : Resource.DELTA_DT_NEO_60K_PHYSICAL;
        } else if (neoconfig40KFlag) {
            resource = isEnvCloud() ? Resource.DELTA_DT_CLOUD : Resource.DELTA_DT_NEO_40K_PHYSICAL;
        } else {
            resource = isEnvCloud() ? Resource.DELTA_DT_CLOUD : Resource.DELTA_DT_NEO_40K_PHYSICAL;
        }
        return resource.getJson();
    }

    private enum Resource {
        DELTA_DT_NEO_60K_PHYSICAL("/json/delta.downtime.dependencies_neo_60k_physical.json"),
        DELTA_DT_NEO_40K_PHYSICAL("/json/delta.downtime.dependencies_neo_40k_physical.json"),
        DELTA_DT_CLOUD("/json/delta.downtime.dependencies_cloud.json"),
        DELTA_DT_CLOUD_NATIVE("/json/delta.downtime.dependencies_cloud_native.json"),
        DELTA_DT_CLOUD_NATIVE_APP_IMAGE("/json/cENM.app.image.dependencies.json"),
        DELTA_DT_CLOUD_NATIVE_INFRA_IMAGE("/json/cENM.infra.image.dependencies.json");
        private final String json;

        Resource(final String json) {
            this.json = json;
        }

        public String getJson() {
            return json;
        }
    }

    public static void initImageDependencyMap() {
        initImageDependency("app");
        initImageDependency("infra");
    }

    private static void initImageDependency(final String resourceType) {
        Object parse = null;
        final String resourceJson = getJsonImageResource(resourceType);
        try (final InputStream inputStream = HAPropertiesReader.class.getResourceAsStream(resourceJson)) {
            parse = JSONValue.parse(inputStream);
        } catch (final IOException e) {
            logger.warn("Failed to read {}", resourceJson, e);
        }
        if (parse instanceof net.minidev.json.JSONObject) {
            final net.minidev.json.JSONObject jsonObject = (net.minidev.json.JSONObject) parse;
            dependencyThreshholdCloudNative.forEach((dependency, threshold) -> {
                final List<String> imageList = new ArrayList<>();
                final Object images = jsonObject.get(dependency);
                if (images != null) {
                    for (final Object str : (net.minidev.json.JSONArray) (images)) {
                        imageList.add(str.toString());
                    }
                }
                if (resourceType.equalsIgnoreCase("app")) {
                    appImageDependencyMap.put(dependency, imageList);
                } else {
                    infraImageDependencyMap.put(dependency, imageList);
                }
            });
        }
        if (resourceType.equalsIgnoreCase("app")) {
            logger.info("List of app dependencies Map: {}", applicationDependencyMap);
        } else {
            logger.info("List of infra dependencies Map: {}", infraImageDependencyMap);
        }
    }

    public static void initApplicationDependencyMap() {
        Object parse = null;
        final String resourceJson = getJsonResorce();
        try (final InputStream inputStream = HAPropertiesReader.class.getResourceAsStream(resourceJson)) {
            parse = JSONValue.parse(inputStream);
        } catch (final IOException e) {
            logger.warn("Failed to read {}", resourceJson, e);
        }
        if (parse instanceof net.minidev.json.JSONObject) {
            final net.minidev.json.JSONObject jsonObject = (net.minidev.json.JSONObject) parse;
            appMap.forEach((area, appName) -> {
                final List<String> dependencyList = new ArrayList<>();
                final Object dependencies = jsonObject.get(appName);
                if (dependencies != null) {
                    for (final Object str : (net.minidev.json.JSONArray) (dependencies)) {
                        dependencyList.add(str.toString());
                    }
                }
                applicationDependencyMap.put(appName, dependencyList);
            });
        }
        logger.info("List of application dependencies Map: {}", applicationDependencyMap);
    }

    public static Map<String, List<String>> getApplicationDependencyMap() {
        return applicationDependencyMap;
    }

    public static Map<String, List<String>> getImageDependencyMap() {
        return infraImageDependencyMap;
    }

    public static long getHttpSessionTimeout() {
        return httpSessionTimeout;
    }

    public static String getWlProfiles() {
        return wlp;
    }

    public static String getSfsIlo1Ip() {
        return sfsIlo1Ip;
    }

    public static String getSfsIlo2Ip() {
        return sfsIlo2Ip;
    }

    public static String getSfsIloUsername() {
        return sfsIloUsername;
    }

    public static String getSfsIloPassword() {
        return sfsIloPassword;
    }

    public static String getSfsClusterUsername() {
        return sfsClusterUsername;
    }

    public static String getSfsClusterPassword() {
        return sfsClusterPassword;
    }

    public static String getSfsClusterIp() {
        return sfsClusterIp;
    }

    public static Long getUpgradeStartTime() {
        return upgradeStartTime;
    }

    public static Map<String, Map<String, Long>> getFunctionalAreasThresholdMap() {
        return functionalAreasThresholdMap;
    }

    public static Long getCmbeTimeToWaitJobComplete() {
        return cmbeTimeToWaitJobComplete;
    }

    public static long getFmWaitingTimePerAlarm() {
        return fmWaitingTimePerAlarm;
    }

    public static Long getPmTimeToWaitForSubscription() {
        return pmTimeToWaitForSubscription;
    }

    public static int getTimeToWaitForServiceOnline() {
        return timeToWaitForServiceOnline;
    }

    public static int getNumberOfAlarms() {
        return numberOfAlarms;
    }

    public static Host getMS() {
        return MS;
    }

    public static long getWaitForUpgradeToStart() {
        return waitForUpgradeToStart;
    }

    public static Boolean isRunOnce() {
        return runOnce;
    }

    public static String getEnmPassword() {
        return enmPassword;
    }

    public static String getEnmUsername() {
        return enmUsername;
    }

    public static String getHaPassword() {
        return haPassword;
    }

    public static String getHausername() {
        return haUsername;
    }

    public static String getHatoolsUrl() {
        return hatoolsUrl;
    }

    public static Map<String, Boolean> getFunctionalAreasMap() {
        return Collections.unmodifiableMap(functionalAreasMap);
    }

    public static int getTimeToSleep() {
        return timeToSleep;
    }

    public static int getRopTime() {
        return ropTime;
    }

    public static long getMaxDisruptionTime() {
        return maxDisruptionTime;
    }

    public static String getNetsimConfig() {
        return netsimConfig;
    }

    public static String getRadioNetsimConfig() {
        return radioNetsimConfig;
    }

    public static Long getTimeToWaitNodesToSync() {
        return timeToWaitNodesToSync;
    }

    public static String getTestType() {
        return testType;
    }

    /***
     * Gets the Netsim Properties.
     *
     * @param property        the property
     * @param defaultProperty default Property
     */
    private static String getNetsimProperties(final String property, final String defaultProperty) {
        final Object tafList = DataHandler.getAttribute(property);
        String attribute;
        if (tafList instanceof Array || tafList instanceof List) {
            attribute = String.join(",", (Iterable<? extends CharSequence>) tafList);
        } else {
            attribute = (String) tafList;
        }

        if (StringUtils.isNotEmpty(attribute)) {
            return attribute.trim();
        } else {
            logger.info("netsim property {} from DataHandler was not configured: ", property);
            return PropertiesReader.properties.getProperty(property, defaultProperty).trim();
        }
    }

    public static String getProperty(final String property, final String defaultProperty) {
        final Object attribute = DataHandler.getAttribute(property);
        if (attribute != null && !((String) attribute).isEmpty()) {
            return ((String) attribute).trim();
        } else {
            return PropertiesReader.properties.getProperty(property, defaultProperty).trim();
        }
    }

    private static Map<String, String> fillHostList(final String s) {
        final Map<String, String> host = new HashMap<>();
        host.put("ilo.password", PropertiesReader.properties.getProperty(s + "_ilo.password", "").trim());
        host.put("ipv6", PropertiesReader.properties.getProperty(s + ".ipv6", "").trim());
        return host;
    }

    public static String getVisinamingnb() {
        if (isEnvCloudNative()) {
            return "";
        }
        return HostConfigurator.getNorthboundNamingServiceHost().getIp();
    }

    public static String getFmVipAddress() {
        if (!HAPropertiesReader.envCloudNative) {
            return "";
        }
        //cENM sed fetch
        String documentId = getCenmSedDocumentId();
        logger.info("SED documentId for fm_vip_address: {} ", documentId);
        String fmVipAddress = "";
        try {
            if (!documentId.isEmpty()) {
                URL ipUrl = new URL(DOC_URL + documentId);
                String str = getResponseFromUrl(ipUrl);
                org.json.JSONObject data = new org.json.JSONObject(str);
                org.json.JSONObject content = data.getJSONObject("content");
                org.json.JSONObject params = content.getJSONObject("parameters");
                fmVipAddress = (String) params.get("fm_vip_address");
                logger.info("fm_vip_address from cENM sed doc: {}", fmVipAddress);
            }
        } catch (final Exception e) {
            logger.warn("Failed to get fm_vip_address form  cENM sed document : {}", e.getMessage());
        }

        //legacy fetch.
        documentId = getCenmIntegrationDocumentId();
        logger.info("cENM_integration_values documentId for fm_vip_address: {} ", documentId);
        try {
            if (!documentId.isEmpty()) {
                URL ipUrl = new URL(DOC_URL + documentId);
                String str = getResponseFromUrl(ipUrl);
                org.json.JSONObject data = new org.json.JSONObject(str);
                org.json.JSONObject content = data.getJSONObject("content");
                org.json.JSONObject global = content.getJSONObject("global");
                org.json.JSONObject vips = global.getJSONObject("vips");
                fmVipAddressFromValuesDoc = (String) vips.get("fm_vip_address");
                logger.info("fm_vip_address from cENM_integration_values doc : {}", fmVipAddressFromValuesDoc);
            }
        } catch (Exception e) {
            logger.warn("Error occurred in getting fm_vip_address from cENM_integration_values: {}", e.getMessage());
        }
        if (fmVipAddress != null && !fmVipAddress.isEmpty()) {
            return fmVipAddress;
        } else {
            return fmVipAddressFromValuesDoc;
        }
    }

    public static String getSecServLoadBalancerIP() {
        if (!HAPropertiesReader.envCloudNative) {
            return "";
        }
        //cENM sed fetch
        String documentId = getCenmSedDocumentId();
        logger.info("SED documentId for secServLoadBalancerIP: {} ", documentId);
        String loadBalancerIP = "";
        try {
            if (!documentId.isEmpty()) {
                URL ipUrl = new URL(DOC_URL + documentId);
                String str = getResponseFromUrl(ipUrl);
                org.json.JSONObject data = new org.json.JSONObject(str);
                org.json.JSONObject content = data.getJSONObject("content");
                org.json.JSONObject params = content.getJSONObject("parameters");
                loadBalancerIP = (String) params.get("securityServiceLoadBalancerIP");
                logger.info("securityServiceLoadBalancerIP from cENM sed doc: {}", loadBalancerIP);
            }
        } catch (final Exception e) {
            logger.warn("Failed to get securityServiceLoadBalancerIP from cENM sed document: {}", e.getMessage());
        }

        //legacy fetch.
        documentId = getCenmIntegrationDocumentId();
        logger.info("cENM_integration_values documentId for secServLoadBalancerIP: {} ", documentId);
        try {
            if (!documentId.isEmpty()) {
                URL ipUrl = new URL(DOC_URL + documentId);
                String str = getResponseFromUrl(ipUrl);
                org.json.JSONObject data = new org.json.JSONObject(str);
                org.json.JSONObject content = data.getJSONObject("content");
                org.json.JSONObject global = content.getJSONObject("global");
                org.json.JSONObject loadBalancerIPs = global.getJSONObject("loadBalancerIP");
                secServLoadBalancerIPFromValuesDoc = (String) loadBalancerIPs.get("securityServiceLoadBalancerIP");
                logger.info("securityServiceLoadBalancerIP from cENM_integration_values doc : {}", secServLoadBalancerIPFromValuesDoc);
            }
        } catch (Exception e) {
            logger.warn("Error occurred in getting securityServiceLoadBalancerIP from cENM_integration_values: {}", e.getMessage());
        }

        if (loadBalancerIP != null && !loadBalancerIP.isEmpty()) {
            return loadBalancerIP;
        } else {
            return secServLoadBalancerIPFromValuesDoc;
        }
    }

    public static String getCenmSedDocumentId() {
        String documentId = "";
        try {
            URL url = new URL(SED_URL + tafConfigDitDeploymentName);
            String documentIdString = getResponseFromUrl(url);
            documentIdString = documentIdString.substring(1, documentIdString.length() - 2);
            final org.json.JSONObject jsonObject = new org.json.JSONObject(documentIdString);
            try {
                final org.json.JSONObject json = (org.json.JSONObject) jsonObject.get("enm");
                documentId = (String) json.get("sed_id");
                return documentId;
            } catch (final Exception e) {
                logger.warn("Failed to read cENM sed document id : {}", e.getMessage());
            }
        } catch (final Exception e) {
            logger.warn("Error occurred in reading sed id from dit : {}", e.getMessage());
        }
        return "";
    }

    public static String getCenmIntegrationDocumentId() {
        String documentId = "";
        try {
            logger.info("Reading value from cENM_integration_values document ...");
            URL url = new URL(SED_URL + tafConfigDitDeploymentName);
            String documentIdString = getResponseFromUrl(url);
            documentIdString = documentIdString.substring(1, documentIdString.length() - 2);
            final org.json.JSONObject jsonObject = new org.json.JSONObject(documentIdString);
            final org.json.JSONArray jsonArray = (org.json.JSONArray) jsonObject.get("documents");
            for (final Object array : jsonArray) {
                if (((String) ((org.json.JSONObject) array).get("schema_name")).contains("cENM_integration_values")) {
                    try {
                        documentId = (String) ((org.json.JSONObject) array).get("document_id");
                        return documentId;
                    } catch (final Exception e) {
                        logger.warn("Failed to read cENM_integration_values document id : {}", e.getMessage());
                    }
                }
            }
        } catch (final Exception e) {
            logger.warn("Error occurred in getting value from cENM_integration_values: {}", e.getMessage());
        }
        return "";
    }

    public static String getCorbaServerLatestVersion() {
        final String nexusCorbaUrl = "https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/releases/com/ericsson/oss/nbi/fm/ERICcorbaserver_CXP9031152/maven-metadata.xml";
        try {
            URL url = new URL(nexusCorbaUrl);
            String response = HAPropertiesReader.getResponseFromUrl(url);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(response)));
            String corbaReleaseVersion = document.getElementsByTagName("release").item(0).getTextContent().trim();
            logger.info("corbaReleaseVersion : {}", corbaReleaseVersion);
            return corbaReleaseVersion;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            logger.warn("error in parsing xml response, error message : {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("unable get corba server version, error message : {}", e.getMessage());
        }
        return "";
    }

    public static String getResponseFromUrl(URL url) {
        String finalResult = "";
        try {
            logger.info("url : {}", url);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.connect();
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuffer buffer = new StringBuffer();
            while ((line = rd.readLine()) != null) {
                buffer.append(line).append("\n");
            }
            finalResult = buffer.toString();
            rd.close();
            conn.disconnect();
        } catch (final Exception e) {
            e.printStackTrace();
        }
        logger.info("finalResult : {}", finalResult);
        return finalResult;
    }

    private static boolean getHaValueFromJson() {
        String documentId = "";
        String sedId = "";
        boolean HaValueFromJson = false;
        boolean vioMultiTech = false;
        boolean vioTransportOnly = false;
        boolean vio = false;
        try {
            URL url = new URL(SED_URL + tafConfigDitDeploymentName);
            String documentIdString = getResponseFromUrl(url);
            documentIdString = documentIdString.substring(1, documentIdString.length() - 2);
            final org.json.JSONObject jsonObject = new org.json.JSONObject(documentIdString);
            final org.json.JSONArray jsonArray = (org.json.JSONArray) jsonObject.get("documents");
            for (final Object array : jsonArray) {
                if (((String) ((org.json.JSONObject) array).get("schema_name")).contains("vnflcm_sed_schema")) {
                    try {
                        documentId = (String) ((org.json.JSONObject) array).get("document_id");
                        break;
                    } catch (final Exception e) {
                        documentId = "";
                    }
                }
            }
            logger.info("documentId : {} ", documentId);
            if (!documentId.isEmpty()) {
                URL HaUrl = new URL(DOC_URL + documentId);
                String HaString = getResponseFromUrl(HaUrl);
                org.json.JSONObject HAjsonObject = new org.json.JSONObject(HaString);
                try {
                    HaValueFromJson = (boolean) HAjsonObject.get("ha");
                } catch (final Exception e) {
                    HaValueFromJson = false;
                }
                logger.info("HaValueFromJson : " + HaValueFromJson);
            }
            final org.json.JSONObject enmSedJsonObject = (org.json.JSONObject) jsonObject.get("enm");
            sedId = (String) (enmSedJsonObject).get("sed_id");
            logger.info("sedId : {} ", sedId);
            if (!sedId.isEmpty()) {
                URL sedUrl = new URL(DOC_URL + sedId);
                String sedString = getResponseFromUrl(sedUrl);
                org.json.JSONObject sedJsonObject = new org.json.JSONObject(sedString);
                try {
                    vioMultiTech = (boolean) sedJsonObject.get("vioMultiTech");
                    vioTransportOnly = (boolean) sedJsonObject.get("vioTransportOnly");
                    vio = (boolean) sedJsonObject.get("vio");
                } catch (final Exception e) {
                    vioMultiTech = false;
                    vioTransportOnly = false;
                    vio = false;
                }
                logger.info("vioMultiTech : " + vioMultiTech + "vioTransportOnly : " + vioTransportOnly + "vio : " + vio);
            }
            if (vioMultiTech || vioTransportOnly || vio) {
                VIOENV = true;
                logger.info("VIOENV :" + VIOENV);
            }

        } catch (final Exception e) {
            logger.info("exception in json fetchng : {}" , e.getMessage());
            return HaValueFromJson;
        }
        return HaValueFromJson;
    }

}
