package com.ericsson.nms.rv.core.workload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;

public class WorkLoadHandler {
    private static final String ONE_DAY_IN_SECONDS = "86400";
    private static final Logger logger = LogManager.getLogger(WorkLoadHandler.class);
    private static final Host WORKLOAD = HAPropertiesReader.getWorkload();
    private static final Map<String, Boolean> referenceClashProfile = new HashMap<>();
    private static final Map<String, List<String>> mapAppsProfiles = new HashMap<>();
    private static final String WORK_LOAD_VM_IS_NOT_AVAILABLE = "WorkLoad VM is not available";
    private static CliShell wlShell;
    private static ArrayList<String> fmxprofileList = new ArrayList<String>();

    static {
        if (WORKLOAD == null) {
            logger.warn(WORK_LOAD_VM_IS_NOT_AVAILABLE);
        } else {
            wlShell = new CliShell(WORKLOAD);
        }
    }

    public void checkClashProfileFlag(final long currentTimeMillis) {
        try {
            if (wlShell != null) {
                final String profiles = getProfiles();
                if (profiles.isEmpty()) {
                    logger.warn("Nothing to check, 'wl.profiles=' is EMPTY");
                } else {
                    for (final String strValue : profiles.split(",")) {
                        referenceClashProfile.put(strValue.toLowerCase(), true);
                    }
                    waitForAllClashingProfilesSleeping(currentTimeMillis, profiles);
                }
            } else {
                logger.warn(WORK_LOAD_VM_IS_NOT_AVAILABLE);
            }
        } catch(final Exception e) {
            logger.warn("Exception in checkClashProfileFlag {}",e.getMessage());
        }
    }

    private void waitForAllClashingProfilesSleeping(final long currentTimeMillis, final String profiles) {
        final String getClashProfileStatus = "/opt/ericsson/enmutils/bin/workload status %s --no-ansi | grep -E '%s'";
        do {
            final CliResult execResult = wlShell.execute(String.format(getClashProfileStatus, profiles, profiles.replace(",", "|")));
            if (execResult.isSuccess()) {
                if (!execResult.getOutput().isEmpty() && !execResult.getOutput().contains("not running")) {
                    final String clashProfilesStatus = execResult.getOutput();
                    logger.info("List of sleeping profiles check :\n{}", clashProfilesStatus);

                    if (isAllClashingProfilesSleeping(clashProfilesStatus)) {
                        break;
                    } else {
                        sleep(20);
                    }
                }
            } else {
                logger.warn("Failed to get Profiles status, [{}]", execResult);
                sleep(10);
            }
        } while (System.nanoTime() - currentTimeMillis < 6L * 60 * Constants.TEN_EXP_9);
    }

    public void clashingProfileSetFlagExecution(final boolean flag) {
        try {
            if (wlShell != null) {
                logger.info("workload ip is: {}", WORKLOAD.getIp());
                String executionCommand = "/opt/ericsson/enmutils/bin/persistence set upgrade_run %b ";
                if (flag) {
                    executionCommand += ONE_DAY_IN_SECONDS;
                } else {
                    executionCommand += "INDEFINITE";
                }
                if (wlShell.execute(String.format(executionCommand, flag, ONE_DAY_IN_SECONDS)).isSuccess()) {
                    logger.info("Clashing profile -Set flag command executed successfully ");
                } else {
                    logger.warn("Problem in executing Clashing profile -Set flag command ");
                }
            } else {
                logger.warn(WORK_LOAD_VM_IS_NOT_AVAILABLE);
            }
        }catch(final Exception e){
            logger.warn("In Catch block :Problem in executing Clashing profile -Set flag command {}" ,e.getMessage());
        }
    }

    public boolean isClashProfileStopped(final FunctionalArea area) {
        final String key = area.get();
        for (final String profile : mapAppsProfiles.get(key)) {
            if (!referenceClashProfile.getOrDefault(profile.toLowerCase(), true)) {
                HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.appMap.get(key),HAPropertiesReader.appMap.get(key) +", Clashing profile Error");
                return false;
            }
        }
        return true;
    }

    private String getProfiles() {
        final String wlProfiles = HAPropertiesReader.getWlProfiles();
        String profileNames = "";
        if (!wlProfiles.isEmpty()) {
            final StringBuilder profileNameList = new StringBuilder();
            final String[] allFaProfiles = wlProfiles.split(";");
            //split string between functional area / workload profile names
            for (final String profileByFA : allFaProfiles) {
                final String[] profiles = profileByFA.split(":");
                //split string between workload profile names
                final String[] faProfiles = profiles[1].split("\\.");
                final List<String> profilesList = new ArrayList<>();
                for (final String profileName : faProfiles) {
                    profileNameList.append(profileName).append(",");
                    profilesList.add(profileName);
                }
                mapAppsProfiles.put(profiles[0], profilesList);
            }
            profileNames = profileNameList.toString().substring(0, profileNameList.length() - 1);
        } else {
            logger.info("Nothing to check, 'wl.profiles' is EMPTY");
        }
        return profileNames;
    }

    private boolean isAllClashingProfilesSleeping(final String clashProfilesStatus) {
        boolean returnValue = true;

        final String[] profileValue = clashProfilesStatus.split("\n");
        for (final String strValue : profileValue) {
            final String[] values = strValue.split("\\s+");
            final String profile = values[0];
            final String status = values[1];
            if (status.equalsIgnoreCase("SLEEPING")) {
                referenceClashProfile.put(profile, true);
            } else {
                returnValue = false;
            }
            logger.info("Sleeping status of profile : {} is : {}.", profile, status);
        }

        //printing final updated map values
        for (final Map.Entry<String, Boolean> entry : referenceClashProfile.entrySet()) {
            logger.info("Key :: {}, Value :: {}", entry.getKey(), entry.getValue());
        }
        return returnValue;
    }

    private void sleep(final int timeInSeconds) {
        try {
            Thread.sleep((long) timeInSeconds * (long) Constants.TEN_EXP_3);
        } catch (final InterruptedException ignore) {
            logger.warn("%s sec sleep interrupted", timeInSeconds, ignore);
        }
    }

    public void fmxclashingProfileExecution(final String flagValue, final String profileNames) {
        try {
            final CliResult execResult;
            if (wlShell != null) {
                if (flagValue.equalsIgnoreCase("stop")) {
                    final String getClashProfileStatus = "/opt/ericsson/enmutils/bin/workload status %s --no-ansi | grep -E '%s'";
                    final String command = String.format(getClashProfileStatus, profileNames, profileNames.replace(",", "|"));
                    logger.info("Execution profile command:\n {}", command);
                    execResult = wlShell.execute(command);
                    if (execResult.isSuccess()) {
                        logger.info("List of Clashing profiles status :\n{}", execResult.getOutput());
                        if (!execResult.getOutput().isEmpty() && !execResult.getOutput().contains("not running")) {
                            final String clashProfilesStatus = execResult.getOutput();
                            final String[] profileValue = clashProfilesStatus.split("\n");
                            for (final String strValue : profileValue) {
                                final String[] value = strValue.split("\\s+");
                                final String profile = value[0];
                                if (profileNames.contains(profile)) {
                                    logger.info("Stopping profile : {}", profile);
                                    executeFmxcommand(flagValue, profile);
                                    fmxprofileList.add(profile);
                                    logger.info("Successfully stopped {} clashing profiles.", profile);
                                }
                            }
                        }
                    } else {
                        logger.info(" Failed to stop FMX_01,FMX_05 clashing profiles");
                    }
                } else {
                    for (String fmxProfile : fmxprofileList) {
                        logger.info("String Fxm clashing profile: {}", fmxProfile);
                        executeFmxcommand(flagValue, fmxProfile);
                    }
                }
            } else {
                logger.warn(WORK_LOAD_VM_IS_NOT_AVAILABLE);
            }
        }
        catch(final Exception e){
            logger.warn("failed to {} fmx {}",flagValue,e.getMessage());
        }
    }

    private void executeFmxcommand(final String flagValue, final String profileName) {
        final CliResult execResult;
        String executionCommand = "/opt/ericsson/enmutils/bin/workload %s %s";
        logger.info("fmx clashing profile command [{}]", String.format(executionCommand, flagValue, profileName));
        execResult = wlShell.execute(String.format(executionCommand, flagValue, profileName));
        if (execResult.isSuccess()) {
            logger.info("command of Fmx Clashing profiles executed successfully ");
        } else {
            logger.warn("Problem in executing command of Fmx Clashing profiles ");
        }

    }

    public boolean checkProfileStatus(final String profile) {
        String profileStatusCmd = "/opt/ericsson/enmutils/bin/workload status | grep %s";
        final CliResult execResult = wlShell.execute(String.format(profileStatusCmd, profile));
        logger.info("Ha_01 Status  {}", execResult.getOutput());
        if (!execResult.getOutput().isEmpty()) {
            logger.info("Profile Ha_01 is running");
            return true;
        } else {
            return false;
        }
    }
}
