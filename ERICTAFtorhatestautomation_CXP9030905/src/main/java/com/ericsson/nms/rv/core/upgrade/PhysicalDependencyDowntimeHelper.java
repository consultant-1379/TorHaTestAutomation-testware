package com.ericsson.nms.rv.core.upgrade;

import static com.ericsson.nms.rv.core.upgrade.CommonDependencyDowntimeHelper.getStringBuilder;
import static java.lang.Math.abs;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.nms.rv.core.CommonReportHelper;
import com.ericsson.nms.rv.core.EnmSystemException;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.ericsson.sut.test.cases.HardResetHost;
import com.ericsson.sut.test.cases.KillPostgres;

public class PhysicalDependencyDowntimeHelper extends UpgradeVerifier {

    private static final Logger logger = LogManager.getLogger(PhysicalDependencyDowntimeHelper.class);
    private static CliShell SHELL_DB1;
    private static CliShell SHELL_DB2;
    private static CliShell SHELL_DB3;
    private static CliShell SHELL_DB4;
    private static final String NEO4J_ONLINE = "%s.*.is.online.on.*.VCS.initiated";
    private static final String NEO4J_OFFLINE = "Initiating.Offline.of.Resource.%s.*.on.System";
    private static final String INITIATING_OFFLINE_OF_RESOURCE = "Initiating.Offline.of.Resource.%s";
    private static final String COMPLETED_OPERATION_ONLINE = "%s.*.is.online.on.*.VCS.initiated";
    public static CliShell SHELL_SVC1;
    public static CliShell SHELL_SVC2;
    public static final List<CliShell> Db = new ArrayList<>();
    private String host = "";

    public static final Map<String, Long> appPostgresUpliftOffsetMap = new HashMap<>();
    public static final Map<String, Map<String, String>> postgresUpliftMap = new HashMap<>();
    private static final String RES_APP_DB_CLUSTER = "Res_App_db_cluster_";
    private static final String ELASTIC_SEARCH = RES_APP_DB_CLUSTER + "elasticsearch_elasticsearch";

    static {
        if(!HAPropertiesReader.isEnvCloudNative()) {
            SHELL_DB1 = new CliShell(HostConfigurator.getDb1());
            SHELL_DB2 = new CliShell(HostConfigurator.getDb2());
            //Db.add(SHELL_DB1);
            Db.add(SHELL_DB2);
            try {
                if (HostConfigurator.getDb3() != null) {
                    SHELL_DB3 = new CliShell(HostConfigurator.getDb3());
                    Db.add(SHELL_DB3);
                }
            } catch (final Exception n) {
                logger.warn("Exception in init DB3.");
            }
            try {
                if (HostConfigurator.getDb4() != null) {
                    SHELL_DB4 = new CliShell(HostConfigurator.getDb4());
                    Db.add(SHELL_DB4);
                }
            } catch (final Exception n1) {
                logger.warn("Exception in init DB4.");
            }
            SHELL_SVC1 = new CliShell(HostConfigurator.getSVC1());
            SHELL_SVC2 = new CliShell(HostConfigurator.getSVC2());
        }
    }

    public PhysicalDependencyDowntimeHelper() {
    }

    public static Map<String, Long> removePostgresUpliftOffset(Map<String, Map<Date, Date>> appDateTimeMap, final Map<String, Long> deltaDowntimeValues) {
        try {
            calculatePostgresUpliftOffset(appDateTimeMap);
            appPostgresUpliftOffsetMap.forEach((appName, offset) -> {
                if (deltaDowntimeValues.get(appName) != null && deltaDowntimeValues.get(appName) != -1) {
                    try {
                        logger.info("deltaDowntimeValues for {} is : {}", appName, deltaDowntimeValues.get(appName));
                        final long newDeltaDownTimeValue = deltaDowntimeValues.get(appName) - offset;
                        logger.info("newDeltaDownTimeValue for {} is : {}", appName, newDeltaDownTimeValue);
                        deltaDowntimeValues.replace(appName, deltaDowntimeValues.get(appName), (newDeltaDownTimeValue > 0 ? newDeltaDownTimeValue : 0L));
                    } catch (final Exception e) {
                        logger.info("Error occurs during postgres uplift offset removal : {}", e.getMessage());
                    }
                }

            });
        } catch (Exception e) {
            logger.warn("Exception occurred in removePostgresUpliftOffset : {} \n {}", e.getMessage(), e.getStackTrace());
        }
        return deltaDowntimeValues;
    }

    private static void calculatePostgresUpliftOffset(Map<String, Map<Date, Date>> appDateTimeMap) {
        logger.info("calculatePostgresUpliftOffset.");
        logger.info("appDateTimeMap : {}", appDateTimeMap);
        logger.info("postgresUpliftMap : {}", postgresUpliftMap);
        if(appDateTimeMap.isEmpty() || postgresUpliftMap.isEmpty()) {
            return;
        }
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ZoneId defaultZoneId = ZoneId.systemDefault();
        appDateTimeMap.forEach((appName, appTime) -> {
            if(!postgresUpliftMap.containsKey(appName)){
                return;
            }
            Duration postgresOffset = Duration.ZERO;
            logger.info("Postgres Uplift calculation started for : app {}, appTime : {}", appName, appTime);
            Map<String, String> postgresDatabaseUpliftMap = postgresUpliftMap.get(appName);
            final Map<LocalDateTime, LocalDateTime> combinedMap = new TreeMap<>();
            postgresDatabaseUpliftMap.forEach((database, timeList)->{
                logger.info("database : {}, timeList : {}", database, timeList);
                Map<LocalDateTime, LocalDateTime> tempMap = new TreeMap<>();
                if(timeList != null && !timeList.isEmpty()) {
                    String[] pair = timeList.split(";");
                    for (final String str : pair) {
                        String[] time = str.split(",");
                        if (time[0] == null || time[1] == null || time[0].isEmpty() || time[1].isEmpty()) {
                            continue;
                        }
                        LocalDateTime haStart = LocalDateTime.parse(time[0], fmt);
                        LocalDateTime haStop = LocalDateTime.parse(time[1], fmt);
                        tempMap.put(haStart, haStop);
                    }
                }
                combinedMap.putAll(mergeMaps(combinedMap, tempMap));
            });

            for (Map.Entry<Date, Date> startStopTime : appTime.entrySet()) {
                final Date appStartDate = startStopTime.getKey();
                final Date appStopDate = startStopTime.getValue();
                try {
                    for (Map.Entry<LocalDateTime, LocalDateTime> entry : combinedMap.entrySet()) {
                        LocalDateTime haStart = entry.getKey();
                        LocalDateTime haStop = entry.getValue();
                        LocalDateTime appStart = appStartDate.toInstant().atZone(defaultZoneId).toLocalDateTime();
                        LocalDateTime appStop = appStopDate.toInstant().atZone(defaultZoneId).toLocalDateTime();

                        postgresOffset = postgresOffset.plus(CommonReportHelper.getCommonOffsetFromTwoDowntimeWindows(appStart, appStop, haStart, haStop));
                    }
                } catch (final Exception ex) {
                    logger.warn(ex.getMessage());
                }
            }
            logger.info("Total postgres uplift offset for Application {} : {}", appName, postgresOffset.getSeconds());
            appPostgresUpliftOffsetMap.put(appName, (postgresOffset.getSeconds() > 0) ? postgresOffset.getSeconds() : 0);
        });
        logger.info("appPostgresUpliftOffsetMap : {}", appPostgresUpliftOffsetMap);
    }

    public static Map<LocalDateTime, LocalDateTime> mergeMaps(Map<LocalDateTime, LocalDateTime> map1, Map<LocalDateTime, LocalDateTime> map2){

        Map<LocalDateTime, LocalDateTime> combinedMap = new TreeMap<>();
        logger.info("map1 {}", map1);
        logger.info("map2 : {}", map2);

        if(map1.isEmpty()) {
            combinedMap.putAll(map2);
        } else if(map2.isEmpty()) {
            combinedMap.putAll(map1);
        } else {
            map1.forEach((date1Start, date1Stop)-> {
                if(map2.isEmpty()){
                    logger.info("map2 is empty");
                    combinedMap.put(date1Start, date1Stop);
                } else {
                    Map<LocalDateTime, LocalDateTime> localMap = new TreeMap<>(map2);
                    for (Map.Entry<LocalDateTime, LocalDateTime> entry : localMap.entrySet()) {
                        boolean flag = true;
                        LocalDateTime date2Start = entry.getKey();
                        LocalDateTime date2Stop = entry.getValue();
                        if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Start) &&
                                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop))) { //one
                            logger.info("condition 1");
                            combinedMap.put(date2Start, date1Stop);
                            map2.remove(date2Start);
                        } else if (date2Start.isBefore(date1Stop) && date2Stop.isAfter(date1Stop) &&
                                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //two
                            logger.info("condition 2");
                            combinedMap.put(date1Start, date2Stop);
                            map2.remove(date2Start);
                        } else if (date2Start.isBefore(date1Stop) && date2Stop.isBefore(date1Stop) &&
                                (date2Start.isAfter(date1Start) || date2Start.isEqual(date1Start))) { //three
                            logger.info("condition 3");
                            combinedMap.put(date1Start, date1Stop);
                            map2.remove(date2Start);
                        } else if (date2Start.isAfter(date1Start) && date2Start.isBefore(date1Stop) &&
                                (date2Stop.isBefore(date1Stop) || date2Stop.isEqual(date1Stop))) {  //four
                            logger.info("condition 4");
                            combinedMap.put(date1Start, date1Stop);
                            map2.remove(date2Start);
                        } else if (date2Start.isAfter(date1Start) && date2Stop.isBefore(date1Stop)) { //included in three
                            logger.info("condition 5");
                            combinedMap.put(date1Start, date1Stop);
                            map2.remove(date2Start);
                        } else if (date2Start.isBefore(date1Start) && date2Stop.isAfter(date1Stop)) {
                            logger.info("condition 6");
                            combinedMap.put(date2Start, date2Stop);
                            map2.remove(date2Start);
                        } else if (date2Start.isEqual(date1Start) && date2Stop.isEqual(date1Stop)) {  //equal window
                            logger.info("condition 7");
                            combinedMap.put(date1Start, date1Stop);
                            map2.remove(date2Start);
                        } else if (date2Start.isAfter(date1Stop)) { //disjoint
                            logger.info("condition 8");
                            combinedMap.put(date1Start, date1Stop);
                            //don't remove map2 have to check in next iteration also
                        } else if (date2Start.isEqual(date1Stop)) { //disjoint
                            logger.info("condition 9");
                            combinedMap.put(date1Start, date2Stop);
                            map2.remove(date2Start);
                        } else if (date2Stop.isBefore(date1Start)) { //disjoint
                            logger.info("condition 10");
                            combinedMap.put(date2Start, date2Stop);
                            map2.remove(date2Start);
                            //don't move to next, map1 have to check with other entries
                            flag = false;
                            if(map2.isEmpty()){
                                logger.info("map2 is empty");
                                combinedMap.put(date1Start, date1Stop);
                            }
                        } else if (date2Stop.isEqual(date1Start)) {
                            logger.info("condition 11");
                            combinedMap.put(date2Start, date1Stop);
                            map2.remove(date2Start);
                        } else {
                            logger.info("no offset ....!");
                        }

                        if (flag) {
                            return;
                        }
                    }
                }
            });
            if(!map2.isEmpty()) {
                combinedMap.putAll(map2);
            }
        }
        map1.clear();
        map2.clear();
        logger.info("combinedMap : " + combinedMap);
        return  combinedMap;
    }


    private static class PostgresUplift {
        private final CliShell shell1;
        private final CliShell shell2;
        private final String app;
        private final List<String> databases = new ArrayList<>();

        public PostgresUplift(CliShell shell1, CliShell shell2, String app, final String ... databases) {
            this.shell1 = shell1;
            this.shell2 = shell2;
            this.app = app;
            this.databases.addAll(Arrays.asList(databases));
        }

        public CliShell getShell1() {
            return shell1;
        }

        public CliShell getShell2() {
            return shell2;
        }

        public String getApp() {
            return app;
        }

        public List<String> getDatabases () {
            return databases;
        }
    }

    private static Map<String, Map<String, String>> getPostgresUpliftMap() {

        final String logFile = "/var/log/messages";
        final String upliftStartString = "'postgresql_version_uplift.*Limiting.connection.on.%s'";
        final String upliftCompleteString = "'postgresql_version_uplift.*Resetting.connection.on.%s'";
        final String[] cmDatabases = {"configds", "sfwkdb"};
        final String[] cmbeDatabases = {"exportds"};
        final String[] cmbilDatabases = {"importdb"};
        final String[] shmDatabases = {"wfsdb_shmcoreserv"};
        final String[] systemDatabases = {"idenmgmt"};

        Map<String, Map<String, String>> postgresUpliftMap = new HashMap<>();
        final Map<String, PostgresUplift> hashMap = new HashMap<>();
        hashMap.put(HAPropertiesReader.CONFIGURATIONMANGEMENT, new PostgresUplift(SHELL_DB1, SHELL_DB2, HAPropertiesReader.CONFIGURATIONMANGEMENT, cmDatabases));
        hashMap.put(HAPropertiesReader.CMBULKEXPORT, new PostgresUplift(SHELL_DB1, SHELL_DB2, HAPropertiesReader.CMBULKEXPORT, cmbeDatabases));
        hashMap.put(HAPropertiesReader.CMBULKIMPORTTOLIVE, new PostgresUplift(SHELL_DB1, SHELL_DB2, HAPropertiesReader.CMBULKIMPORTTOLIVE, cmbilDatabases));
        hashMap.put(HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT, new PostgresUplift(SHELL_DB1, SHELL_DB2, HAPropertiesReader.SOFTWAREHARDWAREMANAGEMENT, shmDatabases));
        hashMap.put(HAPropertiesReader.SYSTEMVERIFIER, new PostgresUplift(SHELL_DB1, SHELL_DB2, HAPropertiesReader.SYSTEMVERIFIER, systemDatabases));
        try {
            hashMap.forEach((app, pair) -> {
                Map<String, String> postgresDatabaseUpliftMap = new HashMap<>();
                pair.getDatabases().forEach((database)-> {
                    List<String> downTimePeriods = new ArrayList<>();
                    try {
                        final String startString = String.format(upliftStartString, database);
                        final String completeString = String.format(upliftCompleteString, database);
                        downTimePeriods = getUpliftTimePeriods(pair.getApp(), pair.getShell1(), startString, completeString, logFile);
                        if(downTimePeriods.isEmpty()) {
                            downTimePeriods = getUpliftTimePeriods(pair.getApp(), pair.getShell2(), startString, completeString, logFile);
                        }
                    } catch (EnmSystemException e) {
                        logger.warn("Error occurred in getUpliftTimePeriods for app : {}, error message : {}", app, e.getMessage());
                    }
                    Map<String, Object> seqMap = new HashMap<>();
                    if (!downTimePeriods.isEmpty()) {
                        seqMap = PhysicalDownTimeSeqHelper.getModifiedSeq(downTimePeriods);
                        logger.info("app : {}, seqMap : {}", app, seqMap);
                    } else {
                        seqMap.put("list", downTimePeriods);
                        seqMap.put("start", "");
                    }
                    logger.info("uplift seqMap for database {} : {}", database, seqMap);
                    postgresDatabaseUpliftMap.putAll(addAppToMapPhysical(database, seqMap));
                });
                postgresUpliftMap.put(app, postgresDatabaseUpliftMap);
            });
        } catch (Exception e) {
            logger.warn("Error occurred in getPostgresUpliftMap : {}", e.getMessage());
        }

        logger.info("postgresUpliftMap : {}", postgresUpliftMap);

        return postgresUpliftMap;
    }

    private static Map<String, String> addAppToMapPhysical(String app, Map<String, Object> seqMap) {
        Map<String, String> map = new HashMap<>();
        try {
            if (seqMap.containsKey("list")) {
                List<String> downTimePeriodsLt = (List<String>) seqMap.get("list");
                if (!downTimePeriodsLt.isEmpty() && downTimePeriodsLt.size() > 1 && downTimePeriodsLt.size() % 2 == 0) {
                    map.put(app, getStringBuilder(downTimePeriodsLt).toString());
                } else if (downTimePeriodsLt.isEmpty()) {
                    map.put(app, "");
                } else if (downTimePeriodsLt.size() > 1 && downTimePeriodsLt.size() % 2 != 0) {
                    map.put(app, getStringBuilder(downTimePeriodsLt).toString());
                }
                logger.warn("app in addAppToMapPhysical : {} = {}", app, map.get(app));
            }
        } catch (final Exception e) {
            logger.info("exception in addAppToMapPhysical : {}", e.getMessage());
        }
        return map;
    }

    private static List<String> getUpliftTimePeriods(final String app, final CliShell shell, final String start, final String finish, final String logFile) throws EnmSystemException {
        final List<String> offList = getUpliftCommandOutput(start, app, shell, logFile);
        final List<String> onList = getUpliftCommandOutput(finish, app, shell, logFile);
        logger.debug("getUpliftTimePeriods for {} offList {}, onList{}", app, offList, onList);
        ArrayList<String> list = PhysicalDownTimeSeqHelper.getCombinedList(offList, onList);
        logger.info("combined list : {}", list);
        return list;
    }

    private static List<String> getUpliftCommandOutput(final String CommandString, final String app, final CliShell shell, final String logFile) throws EnmSystemException {
        List<String> TimeStringsList = new ArrayList<>();
        final StringBuilder stringBuilder = new StringBuilder();
        SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date startDate = new Date();
        Date finishDate = new Date();
        try {
            startDate = inputDateFormat.parse(String.valueOf(upgradeStartTime));
            finishDate = inputDateFormat.parse(String.valueOf(upgradeFinishTime));
        } catch (ParseException e) {
            logger.warn("error occurred in date Parsing : {}", e.getMessage());
        }
        final String upgradeStartTimeString = new SimpleDateFormat("MMM d HH:mm:ss").format(startDate);
        final String upgradeFinishTimeString = new SimpleDateFormat("MMM d HH:mm:ss").format(finishDate);
        final String egrep = "/bin/egrep -h '%s' %s ";
        final String cut = " | /bin/cut -f 1,3,4 -d ' '";
        final String awk = String.format(" | /bin/awk '$0 >=\"%s\" && $0<=\"%s\"'", upgradeStartTimeString, upgradeFinishTimeString);

        stringBuilder.append(String.format(egrep, CommandString, logFile));
        stringBuilder.append(cut)
                .append(awk)
                .append(" | /bin/sort ");

        logger.debug("Going to execute uplift command for app {} command is : [{}]", app, stringBuilder);
        final CliResult execResult = shell.execute(stringBuilder.toString());
        logger.info("uplift command output : \n[{}]", execResult.getOutput());
        if (execResult.isSuccess() && !execResult.getOutput().isEmpty()) {
            final String[] TimeStrings = execResult.getOutput().split("\n");
            inputDateFormat = new SimpleDateFormat("yyyy MMM d HH:mm:ss");
            logger.info("changing the date format: from : {yyyy MMM d HH:mm:ss} to : {yyyyMMddHHmmss}");
            for (int i = 0; i < TimeStrings.length; i++) {
                Date date;
                try {
                    date = inputDateFormat.parse(LocalDateTime.now().getYear() + " "+TimeStrings[i]);
                    logger.info("date : {}", TimeStrings[i]);
                    TimeStrings[i] = new SimpleDateFormat("yyyyMMddHHmmss").format(date);
                    logger.info("before : {}, after : {}", date, TimeStrings[i]);
                } catch (ParseException e) {
                    logger.warn("error occurred in date parsing : {}", e.getMessage());
                } catch (Exception e) {
                    logger.warn("error occurred in changing date format : {}", e.getMessage());
                }
            }

            if (TimeStrings.length > 0) {
                logger.info("converting array to list:");
                TimeStringsList = CommonDependencyDowntimeHelper.buildDowntimeList(TimeStrings);
            } else {
                logger.warn("getDownTimePeriods for {} is EMPTY", app);
                return new ArrayList<>();
            }
        } else if(execResult.isSuccess() && execResult.getOutput().isEmpty()){
            logger.info("uplift command output is EMPTY");
        } else {
            logger.warn("Failed to get DT for {}", app);
            throw new EnmSystemException("Failed to execute command " + app);
        }
        logger.info("TimeStringsList : {}", TimeStringsList);
        return TimeStringsList;
    }

    private boolean isKillPostgresRegression() {
        final boolean isKillPostgresRegression = (HAPropertiesReader.getTestType().equalsIgnoreCase("Regression") &&
                HAPropertiesReader.getTestSuitName().contains("killPostgres"));
        logger.info("isKillPostgresRegression : {}", isKillPostgresRegression);
        return isKillPostgresRegression;
    }

    public Map<String, String> buildKnownDowntimeMapForPhysical() {
        // DB
        final String neo4j ="Res_App_db_cluster_neo4j_cluster_service_neo4j_service";
        final String postgres = RES_APP_DB_CLUSTER + "postgres_postgresql";
        final String jms = RES_APP_DB_CLUSTER + "jms_jms_service";
        final String elasticsearchproxy = RES_APP_DB_CLUSTER + "elasticsearch_elasticsearchproxy";
        final String eshistory = RES_APP_DB_CLUSTER + "eshistory_eshistory_service";

        // SVC
        final String resAppSvcCluster = "Res_App_svc_cluster_";
        final String haproxyExt = resAppSvcCluster + "haproxy_ext_haproxy_ext";
        final String haproxyInt = resAppSvcCluster + "haproxy_int_haproxy_int";
        final String visinamingnb = resAppSvcCluster + "visinamingnb_vm_service_visinamingnb";
        final String openidm = resAppSvcCluster + "openidm_vm_service_openidm";
        final CliShell dbShell;
        final CliShell svcShell;
        if (HAPropertiesReader.isHaltHostHardResetRegression() || isKillPostgresRegression()) {
            if (HAPropertiesReader.isHaltHostHardResetRegression()) {
                host = DataHandler.getAttribute("host.name").toString();
            } else {
                host = KillPostgres.postgresDbHost.toUpperCase();
            }
            logger.info("{} Regression operation on host: {}", HAPropertiesReader.getTestSuitName(), host);
            switch(host) {
                case "SVC1":
                    dbShell = SHELL_DB1;
                    svcShell = SHELL_SVC2;
                    break;
                case "DB1":
                    dbShell = SHELL_DB2;
                    svcShell = SHELL_SVC1;
                    break;
                case "SVC2":
                case "SVC3":
                case "SVC4":
                case "DB2":
                case "DB3":
                case "DB4":
                    dbShell = SHELL_DB1;
                    svcShell = SHELL_SVC1;
                    break;
                default:
                    dbShell = SHELL_DB1;
                    svcShell = SHELL_SVC1;
                    logger.error("Invalid host name : {}", host);
                    break;
            }
        } else {
            dbShell = SHELL_DB1;
            svcShell = SHELL_SVC1;
        }


        final Map<String, Pair> hashMap = new HashMap<>();
        hashMap.put(HAPropertiesReader.POSTGRES, new Pair(dbShell, postgres));
        hashMap.put(HAPropertiesReader.JMS, new Pair(dbShell, jms));
        hashMap.put(HAPropertiesReader.ELASTICSEARCH, new Pair(dbShell, ELASTIC_SEARCH));
        hashMap.put(HAPropertiesReader.ELASTICSEARCHPROXY, new Pair(dbShell, elasticsearchproxy));
        hashMap.put(HAPropertiesReader.ESHISTORY, new Pair(dbShell, eshistory));
        hashMap.put(HAPropertiesReader.HAPROXY, new Pair(svcShell, haproxyExt));
        hashMap.put(HAPropertiesReader.VISINAMINGNB, new Pair(svcShell, visinamingnb));
        hashMap.put(HAPropertiesReader.OPENIDM, new Pair(svcShell, openidm));
        hashMap.put(HAPropertiesReader.HAPROXY_INT, new Pair(svcShell, haproxyInt));
        hashMap.put(HAPropertiesReader.FTS, new Pair(svcShell, ""));
        String coreValue = "";

        //Validate if login to svc-db is working
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 60 * 60 * 1000L) {
            try {
                final CliResult dbResult = dbShell.execute("hostname");
                final CliResult svcResult = svcShell.execute("hostname");
                if (dbResult.isSuccess() && svcResult.isSuccess()) {
                    logger.info("Login is successful on svc and db nodes.");
                    logger.info("Using SVC Hostname  : {}", svcResult.getOutput());
                    logger.info("Using DB Hostname  : {}", dbResult.getOutput());
                    break;
                }
            } catch (final Exception e) {
                logger.warn("Message : {}", e.getMessage());
                logger.warn("svc/db login failed .. retrying");
            }
            CommonUtils.sleep(60);
        }

        if ((HAPropertiesReader.neoconfig40KFlag || HAPropertiesReader.neoconfig60KFlag) && !Db.isEmpty()) {
            CliShell shell = new CliShell(HAPropertiesReader.getMS());

            logger.debug("Getting Neo4j Configuration from Env");
            try {
                coreValue = HAPropertiesReader.getNeo4jConfiguration(shell);
            } catch (Exception e) {
                logger.info("Failed in getting Neo4j configuration details : {}", e.getMessage());
            }
            if(!coreValue.equalsIgnoreCase("")) {
                for (CliShell db : Db) {
                    if (coreValue.equalsIgnoreCase("SINGLE")) {
                        logger.info("Setting neo4j value in dependenciesDTmap");
                        if (HAPropertiesReader.isHaltHostHardResetRegression() || isKillPostgresRegression()) {
                            hashMap.put(HAPropertiesReader.NEO4J, new Pair(dbShell, neo4j));
                        } else {
                            hashMap.put(HAPropertiesReader.NEO4J, new Pair(db, neo4j));
                        }
                        break;
                    } else if (coreValue.equalsIgnoreCase("CORE")) {
                        hashMap.put(HAPropertiesReader.NEO4J_LEADER, new Pair(db, neo4j));
                        break;
                    }
                }
            }
        }

        final Map<String, String> dependenciesDTmap = new HashMap<>();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        try {
            hashMap.forEach((component, pair) -> {
                List<String> downTimePeriods;
                Map<String, Object> seqMap = new HashMap<>();
                int counter = 0;
                if (!component.equalsIgnoreCase(HAPropertiesReader.NEO4J_LEADER)) {
                    do {
                        if (component.equalsIgnoreCase(HAPropertiesReader.NEO4J)) {
                            try {
                                if (HAPropertiesReader.isHaltHostHardResetRegression()) {
                                    String searchString = String.format("Grp_CS_db_cluster_%s_clustered_service", "sg_neo4j");
                                    final String NEO_ONLINE = String.format("Group.%s.is.online.on.system", searchString);
                                    final LocalDateTime startDate;
                                    if (HAPropertiesReader.getTestSuitName().contains("HaltHost")) {
                                        downTimePeriods = getDownTimePeriods(pair.getApp(), pair.getShell(), NEO4J_OFFLINE, NEO4J_ONLINE, ENGINE_ALOG);
                                    } else {
                                        startDate = HardResetHost.hardResetTime;
                                        logger.info("NEO_OFFLINE : {}", startDate.format(formatter));
                                        logger.info("NEO_ONLINE : {}", NEO_ONLINE);
                                        downTimePeriods = getDownTimePeriodRegression(pair.getApp(), pair.getShell(), startDate, NEO_ONLINE, ENGINE_ALOG, host, component);
                                    }
                                } else {
                                    downTimePeriods = getDownTimePeriods(pair.getApp(), pair.getShell(), NEO4J_OFFLINE, NEO4J_ONLINE, ENGINE_ALOG);
                                }
                            } catch (final EnmSystemException e) {
                                logger.info("command failed to execute {}", "NEO4J");
                                downTimePeriods = new ArrayList<>();
                            } catch (final Exception e) {
                                logger.info("NEO4J command failed to execute {}", e.getMessage());
                                downTimePeriods = new ArrayList<>();
                            }
                        } else {
                            try {
                                if (HAPropertiesReader.isHaltHostHardResetRegression()) {
                                    LocalDateTime startDate;
                                    String searchString;
                                    if (isDbComponent(component)) {
                                        searchString = String.format("Grp_CS_db_cluster_%s_clustered_service", component);
                                        if (component.equalsIgnoreCase(HAPropertiesReader.ELASTICSEARCHPROXY)) {
                                            searchString = String.format("Grp_CS_db_cluster_%s_clustered_service", HAPropertiesReader.ELASTICSEARCH);
                                        }
                                    } else {
                                        final String dependency = component.equalsIgnoreCase("haproxy") ? "haproxy_ext" : component;
                                        searchString = String.format("Grp_CS_svc_cluster_%s", dependency);
                                    }
                                    if (HAPropertiesReader.getTestSuitName().contains("HaltHost")) {
                                        if (component.equalsIgnoreCase(HAPropertiesReader.FTS)) {
                                            downTimePeriods = CommonDependencyDowntimeHelper.getFtsDownTimePeriods();
                                        } else {
                                            downTimePeriods = getDownTimePeriods(pair.getApp(), pair.getShell(), INITIATING_OFFLINE_OF_RESOURCE, COMPLETED_OPERATION_ONLINE, UpgradeVerifier.ENGINE_ALOG);
                                        }
                                    } else if(component.equalsIgnoreCase("haproxy") || component.equalsIgnoreCase("haproxy_int")) {
                                        final String OFFLINE_STRING = "System.*(Node.*) changed state from RUNNING to FAULTED";
                                        final String ONLINE_STRING = String.format("Group.%s.is.online.on.system", searchString);
                                        startDate = getHaproxyOfflineInHardReset(OFFLINE_STRING, pair.getShell(), "/var/log/messages*");
                                        logger.info("OFFLINE_DATE : {}", startDate.format(formatter));
                                        logger.info("ONLINE_STRING : {}", ONLINE_STRING);
                                        downTimePeriods = getDownTimePeriodRegression(pair.getApp(), pair.getShell(), startDate, ONLINE_STRING, UpgradeVerifier.ENGINE_ALOG, host, component);
                                    } else if (component.equalsIgnoreCase(HAPropertiesReader.FTS)) {
                                        downTimePeriods = CommonDependencyDowntimeHelper.getFtsDownTimePeriods();
                                    } else {
                                        startDate = HardResetHost.hardResetTime;
                                        final String ONLINE_STRING = String.format("Group.%s.is.online.on.system", searchString);
                                        logger.info("OFFLINE_DATE : {}", startDate.format(formatter));
                                        logger.info("ONLINE_STRING : {}", ONLINE_STRING);
                                        downTimePeriods = getDownTimePeriodRegression(pair.getApp(), pair.getShell(), startDate, ONLINE_STRING, UpgradeVerifier.ENGINE_ALOG, host, component);
                                    }
                                } else if (isKillPostgresRegression() && component.equalsIgnoreCase(HAPropertiesReader.POSTGRES)) {
                                    logger.info("Kill postgres DT calculation .. ");
                                    final LocalDateTime OFFLINE_TIME = KillPostgres.postgresKillTime;
                                    final String ONLINE_OF_POSTGRES = "%s.*.in.online.state.*on";
                                    logger.info("OFFLINE_TIME_POSTGRES : {}", OFFLINE_TIME.format(formatter));
                                    logger.info("ONLINE_OF_POSTGRES : {}", ONLINE_OF_POSTGRES);
                                    downTimePeriods = getDownTimePeriodRegression(pair.getApp(), pair.getShell(), OFFLINE_TIME, ONLINE_OF_POSTGRES, UpgradeVerifier.ENGINE_ALOG, host, component);
                                } else if (component.equalsIgnoreCase(HAPropertiesReader.FTS)) {
                                    downTimePeriods = CommonDependencyDowntimeHelper.getFtsDownTimePeriods();
                                } else {
                                    downTimePeriods = getDownTimePeriods(pair.getApp(), pair.getShell(), INITIATING_OFFLINE_OF_RESOURCE, COMPLETED_OPERATION_ONLINE, UpgradeVerifier.ENGINE_ALOG);
                                }
                            } catch (final EnmSystemException e) {
                                logger.info("command failed to execute {}", component);
                                downTimePeriods = new ArrayList<>();
                            } catch (final Exception e) {
                                logger.info("command failed to execute {} {}", component,e.getMessage());
                                downTimePeriods = new ArrayList<>();
                            }
                            if (component.equalsIgnoreCase(HAPropertiesReader.HAPROXY)) {
                                logger.info("haproxy downtime list is {}", downTimePeriods);
                                logger.info("haproxy downtime list size is {}", downTimePeriods.size());
                                haproxyInstance = ((((downTimePeriods.size()) % 2 == 0 ? (downTimePeriods.size()) / 2 : ((downTimePeriods.size() - 1)) / 2)));
                            }
                        }
                        if (!downTimePeriods.isEmpty()) {
                            seqMap = PhysicalDownTimeSeqHelper.getModifiedSeq(downTimePeriods);
                        } else {
                            seqMap.put("list", downTimePeriods);
                            seqMap.put("start", "");
                        }
                        dependenciesDTmap.putAll(PhysicalDownTimeSeqHelper.addComponentToMapPhysical(component, seqMap));

                    } while (downTimePeriods.isEmpty() && (++counter) < 3);
                }
            });

            List<String> downTimePeriods;
            if (HAPropertiesReader.neoconfig60KFlag) {
                logger.info("Physical NEO4J Leader Calculation Started");
                try {
                    downTimePeriods = CommonDependencyDowntimeHelper.neoDtCalculation("W");
                } catch (final EnmSystemException e) {
                    logger.info("command failed to execute {}", "NEO4J Leader");
                    downTimePeriods = new ArrayList<>();
                } catch (final Exception e) {
                    logger.info("NEO4J Leader command failed to execute {}", e.getMessage());
                    downTimePeriods = new ArrayList<>();
                }
                logger.info("Physical NEO4J - Leader downTimePeriods{}", downTimePeriods);
                dependenciesDTmap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(NEO4J_LEADER, downTimePeriods));
                List<String> downTimePeriodsR;
                logger.info("Physical NEO4J Follower Calculation Started");
                try {
                    downTimePeriodsR = CommonDependencyDowntimeHelper.neoDtCalculation("R");
                } catch (final Exception e) {
                    logger.info("NEO4J Follower command failed to execute : {}", e.getMessage());
                    downTimePeriodsR = new ArrayList<>();
                }
                logger.info("Physical NEO4J - Follower downTimePeriods{}", downTimePeriodsR);
                dependenciesDTmap.putAll(CommonDependencyDowntimeHelper.addComponentToMap(NEO4J_FOLLOWER, downTimePeriodsR));
            }
        } catch (final Exception e) {
            logger.warn("Failed to buildKnownDowntimeMap", e);
        }

        logger.info("getting Postgres Uplift windows for dependent applications");
        try {
            postgresUpliftMap.putAll(getPostgresUpliftMap());
        } catch (Exception e) {
            logger.warn("Failed to get postgresUpliftMap : {}", e.getMessage());
        }
        return dependenciesDTmap;
    }

    private List<String> getDownTimePeriods(final String app, final CliShell shell, final String start, final String finish, final String logFile) throws EnmSystemException {
        final List<String> offList = getCommandOutput(start, app, shell, logFile);
        final List<String> onList = getCommandOutput(finish, app, shell, logFile);
        if (app.contains("jms") && !onList.isEmpty()) {
            final String jmsString = "JMS.*.blockJMSPort.sh.*.Remove.rule.blocking.port.5445.from.iptables";
            final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            List<String> jmsData = new ArrayList<>();
            List<String> newJmsPortList = new ArrayList();
            List<String> toRemove = new ArrayList();
            List<String> toadd = new ArrayList();
            List<CliShell> tempDb = new ArrayList<>();
            String year ="";

            String jmsDateTime = "";
            tempDb.add(SHELL_DB1); //created one more tempDB lis because of there is no entry in existing Db list
            tempDb.add(SHELL_DB2);

            try {
                if (HostConfigurator.getDb3() != null) {
                    SHELL_DB3 = new CliShell(HostConfigurator.getDb3());
                    tempDb.add(SHELL_DB3);
                }
            } catch (final Exception n) {
                logger.warn("Exception in init DB3.");
            }

            try {
                if (HostConfigurator.getDb4() != null) {
                    SHELL_DB4 = new CliShell(HostConfigurator.getDb4());
                    tempDb.add(SHELL_DB4);
                }
            } catch (final Exception n) {
                logger.warn("Exception in init DB4.");
            }


            try {
                SimpleDateFormat ugtimeformatter = new SimpleDateFormat("yyyyMMddHHmmss");
                Date jmsDate = ugtimeformatter.parse(UpgradeVerifier.JMSDATE);
                String [] jmsDateSplit = jmsDate.toString().split(" ");
                year = jmsDateSplit[5];
                jmsDateTime = jmsDateSplit[1] + jmsDateSplit[2];
                for (String dt : jmsDateSplit[3].split(":")) {
                    System.out.println("Jms time value is " + dt);
                    jmsDateTime = jmsDateTime + dt;
                }
            } catch (Exception e) {
                logger.info("Exception in converting JMS DATE to MMMddHHmmss {}",e.getMessage());
            }

            logger.info("Date command output is {} and length of DB is {} ", jmsDateTime, tempDb.size());
            logger.info("JMS onList is ={}", onList);

            for (CliShell db : tempDb) {
                List<String> tempJmsData = getCommandJms(jmsString, db, "/var/log/messages", jmsDateTime);
                if (!tempJmsData.isEmpty()) {
                    jmsData.addAll(tempJmsData);
                }
            }
            logger.info("JMS Port data is ={}", jmsData);
            try {
            if (!jmsData.isEmpty()) {
                for (String jmsD : jmsData) {
                    newJmsPortList.add(year.concat(jmsD));
                }
                logger.info("JMS Port Time(HHmmss) is ={}", newJmsPortList);
                for (String onData : onList) {
                    logger.info("JMS online list item is ={} ", onData);

                    for (String newJmsPortData : newJmsPortList) {
                        try {
                            DateTimeFormatter timeFormatter;
                            if (newJmsPortData.length() == 15) {
                                timeFormatter = DateTimeFormatter.ofPattern("yyyyMMMddHHmmss");
                            } else{ //if date format is yyyyMMMdHHmmss
                                timeFormatter = DateTimeFormatter.ofPattern("yyyyMMMdHHmmss");
                            }

                            logger.info("jms port local time data {}", LocalDateTime.parse(newJmsPortData, timeFormatter));
                            final LocalDateTime localDateTimeStart = LocalDateTime.parse(String.valueOf(newJmsPortData), timeFormatter);

                            final DateTimeFormatter timeFormatter1 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                            logger.info("jms online local time data  {} ", LocalDateTime.parse(onData, timeFormatter1));
                            final LocalDateTime localDateTimeFinish = LocalDateTime.parse(String.valueOf(onData), timeFormatter1);

                            final Duration duration = Duration.between(localDateTimeStart, localDateTimeFinish);

                            logger.info("Duration between JMSport and JMS online is {}", abs(duration.getSeconds()));
                            if (abs(duration.getSeconds()) <= 60) {
                                logger.info("time diff output is {} ---- jmsport convert date", newJmsPortData + onData + Long.parseLong(localDateTimeStart.format(dateTimeFormatter)));
                                toRemove.add(onData);
                                toadd.add(String.valueOf(Long.parseLong(localDateTimeStart.format(dateTimeFormatter))));
                            }
                        } catch (Exception e) {
                            logger.info("error in processing jms port data{}",e.getMessage());
                        }
                    }
                }
                onList.removeAll(toRemove);
                List<String> deduped = toadd.stream().distinct().collect(Collectors.toList());
                logger.info("Removing Duplicated JMS port data {}", deduped);
                onList.addAll(deduped);

            } else {
                logger.info("jmsData is  EMPTY");
            }
            }catch (Exception e){
                logger.warn("Exception while processing JMS PORT DATE {}",e.getMessage());
            }
        }
        logger.debug("getDownTimePeriods for {} offList {} onList{}", app, offList, onList);
        ArrayList<String> list = PhysicalDownTimeSeqHelper.getCombinedList(offList, onList);
        return list;
    }

    private List<String> getDownTimePeriodRegression(final String app, final CliShell shell, final LocalDateTime startDate, final String finish, final String logFile, final String host, final String component) throws EnmSystemException {
        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        final List<String> offList = new ArrayList<>();
        final List<String> onList = new ArrayList<>();
        if (HAPropertiesReader.getTestSuitName().contains("KillPostgres")) {
            if (component.equalsIgnoreCase("postgres")) {
                offList.add(startDate.format(dateTimeFormatter));
                onList.addAll(getCommandOutput(finish, app, shell, logFile));
            }
        } else {
            if (host.toLowerCase().contains("svc") && isSvcComponent(component)) {
                final List<String> finishList = getCommandOutput(finish, app, shell, logFile);
                if (!finishList.isEmpty()) {
                    offList.add(startDate.format(dateTimeFormatter));
                    onList.addAll(finishList);
                }
            } else if (host.toLowerCase().contains("db") && isDbComponent(component)) {
                final List<String> finishList = getCommandOutput(finish, app, shell, logFile);
                if (!finishList.isEmpty()) {
                    offList.add(startDate.format(dateTimeFormatter));
                    onList.addAll(finishList);
                }
            }
        }
        logger.info("Reg offList : {}", offList);
        logger.info("Reg onList : {}", onList);
        return PhysicalDownTimeSeqHelper.getCombinedList(offList, onList);
    }

    private boolean isSvcComponent(final String component) {
        final List<String> svcList = Arrays.asList("haproxy", "haproxy_int", "openidm", "visinamingnb");
        return svcList.contains(component);
    }

    private boolean isDbComponent(final String component) {
        final List<String> dbList = Arrays.asList("neo4j", "jms", "postgres", "elasticsearch", "elasticsearchproxy", "eshistory");
        return dbList.contains(component);
    }

    private List<String> getCommandJms(final String commandString,final CliShell shell, final String logFile,final String jmsDate) {

        final List<String> timeStringsList = new ArrayList<>();
        final String egrep = "/bin/egrep -h '%s' %s ";
        final StringBuilder stringBuilder = new StringBuilder();
        final String awk = String.format(" | /bin/awk '$0 >=\"%s\"'", jmsDate);
        final String cutAndTr = "| /bin/awk '{print $1$2$3}' | /usr/bin/tr -d '\\-/ :'";
        stringBuilder.append(String.format(egrep, commandString, logFile));
        stringBuilder.append(cutAndTr)
                .append(awk);

        logger.debug("Going to execute: [{}]", stringBuilder);

        final CliResult execResult = shell.execute(stringBuilder.toString());
        logger.debug("ExecResult of JMS PORT output is : {}", execResult.getOutput());

        if (execResult.isSuccess() && ! execResult.getOutput().isEmpty()) {
            final String[] timeStrings = execResult.getOutput().split("\\n");
            if (timeStrings.length > 0) {
                for(final String jmsDateTime: timeStrings){
                    try {
                        Date jmsCnv = new SimpleDateFormat("MMMddHHmmss").parse(jmsDateTime);
                        timeStringsList.add(jmsDateTime);
                    } catch(Exception e) {
                        logger.warn("Exception - JMS port date is not correct {}",jmsDateTime);
                    }
                }
            } else {
                logger.warn("getDownTimePeriods for {} is EMPTY", "JMS");
                return new ArrayList<>();
            }
        } else {
            logger.warn("Failed to get DT for {}", "JMS");
        }
        return timeStringsList;
    }

    private class Pair {
        private final CliShell shell;
        private final String app;

        public Pair(final CliShell shell, final String app) {
            this.shell = shell;
            this.app = app;
        }

        public CliShell getShell() {
            return shell;
        }

        public String getApp() {
            return app;
        }
    }

    private LocalDateTime getHaproxyOfflineInHardReset(final String commandString, final CliShell shell, String logFile) throws EnmSystemException {
        List<String> TimeStringsList;
        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        final String app = "haproxy";
        final String command = "date --date=\"$(/bin/egrep -h '" + commandString + "' " + logFile + "  | /bin/cut -b -15 | tail -n 1)\" +\"%Y%m%d%H%M%S\"";

        logger.debug("Going to execute for {} command is : [{}]", app, command);
        final CliResult execResult = shell.execute(command);
        logger.info("{} command output is : {}", app, execResult.getOutput());

        if (execResult.isSuccess()) {
            final String[] TimeStrings = execResult.getOutput().split(",");
            if (TimeStrings.length > 0) {
                TimeStringsList = CommonDependencyDowntimeHelper.buildDowntimeList(TimeStrings);
            } else {
                logger.warn("getDownTimePeriods for {} is EMPTY", app);
                return HardResetHost.hardResetTime;
            }
        } else {
            logger.warn("Failed to get DT for {}", app);
            throw new EnmSystemException("Failed to execute command " + app);
        }
        logger.info("TimeStringsList = {}", TimeStringsList);
        return (TimeStringsList.isEmpty()) ? HardResetHost.hardResetTime : LocalDateTime.parse(TimeStringsList.get(0), dateTimeFormatter);
    }

    private List<String> getCommandOutput(final String CommandString, final String app, final CliShell shell, final String logFile) throws EnmSystemException {
        List<String> TimeStringsList;
        final String egrep = "/bin/egrep -h '%s' %s ";
        final StringBuilder stringBuilder = new StringBuilder();
        final String awk = String.format(" | /bin/awk '$0 >=\"%d\" && $0<=\"%d\"'", upgradeStartTime, upgradeFinishTime);
        final String cutAndTr = " | /bin/cut -f 1,2 -d ' ' | /usr/bin/tr -d '\\-/ :' ";
        stringBuilder.append(String.format(egrep, String.format(CommandString, app), logFile));
        if (app.equalsIgnoreCase(ELASTIC_SEARCH)) {
            stringBuilder.append(" | /bin/egrep -v 'elasticsearchproxy' ");
        }
        stringBuilder.append(cutAndTr)
                .append(awk)
                .append(" | /bin/awk -F ',' '{print $1}' | /bin/sort | /usr/bin/xargs | /bin/sed -e 's/ /,/g'");

        logger.debug("Going to execute for app {} command is : [{}]",app, stringBuilder);
        final CliResult execResult = shell.execute(stringBuilder.toString());
        if (execResult != null) {
            logger.info("execResult : {}", execResult.getOutput());
        }
        if (execResult.isSuccess()) {
            final String[] TimeStrings = execResult.getOutput().split(",");
            if (TimeStrings.length > 0) {
                TimeStringsList = CommonDependencyDowntimeHelper.buildDowntimeList(TimeStrings);
            } else {
                logger.warn("getDownTimePeriods for {} is EMPTY", app);
                return new ArrayList<>();
            }
        } else {
            logger.warn("Failed to get DT for {}", app);
            throw new EnmSystemException("Failed to execute command " + app);
        }
        return TimeStringsList;
    }
}

