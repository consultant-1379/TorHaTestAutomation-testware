package com.ericsson.nms.rv.core.esm;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.core.system.SystemStatus;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import com.ericsson.sut.test.cases.util.FunctionalAreaKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class CnomSystemMonitor extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(CnomSystemMonitor.class);
    final String loginUri = "/esm-server/login";
    final String logoutUri = "/esm-server/logout";
    final String usersUri = "/esm-server/api/users";
    final String variableUri = "/esm-server/api/v2/variables";
    final String defaultPassword = HAPropertiesReader.getProperty("cenm.esm.password", "Test@Passw0rd");
    boolean readyToVerify = false;

    private final HttpRestServiceClient httpRestServiceClient = getHttpRestServiceClient();
    final static String nameSpace = HAPropertiesReader.cEnmName;

    public CnomSystemMonitor(final SystemStatus systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
    }

    @Override
    public void prepare() {

        //login with default pwd
        try {
            final List<String[]> loginData = new ArrayList<>();
            loginData.add(new String[]{"username", "sec_admin"});
            loginData.add(new String[]{"password", defaultPassword});
            final HttpResponse esmLogin = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, loginData, null, loginUri);
            logger.info("First ESM Login ResponseCode : {}", esmLogin.getResponseCode().getCode());
            CommonUtils.sleep(1);
            if (!EnmApplication.acceptableResponse.contains(esmLogin.getResponseCode())) {
                logger.info("Default password login failed, changing password to default ...");
                //login with sec_admin first
                final List<String[]> secAdminLoginData = new ArrayList<>();
                secAdminLoginData.add(new String[]{"username", "sec_admin"});
                secAdminLoginData.add(new String[]{"password", "sec_admin"});
                final HttpResponse secAdminLogin = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, secAdminLoginData, null, loginUri);
                logger.info("sec_admin ESM Login ResponseCode : {}", secAdminLogin.getResponseCode().getCode());
                CommonUtils.sleep(1);
                //Change pwd
                final List<String[]> changePwdData = new ArrayList<>();
                changePwdData.add(new String[]{"username", "sec_admin"});
                changePwdData.add(new String[]{"password", "sec_admin"});
                changePwdData.add(new String[]{"newPassword", defaultPassword});
                logger.info("Changing password to : {}", defaultPassword);
                final HttpResponse esmChangePwd = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, changePwdData, null, loginUri);
                logger.info("ESM esmChangePwd ResponseCode : {}", esmChangePwd.getResponseCode().getCode());
                logger.info("ESM esmChangePwd body : {}", esmChangePwd.getBody());
                CommonUtils.sleep(1);
                final List<String[]> newLoginData = new ArrayList<>();
                newLoginData.add(new String[]{"username", "sec_admin"});
                newLoginData.add(new String[]{"password", defaultPassword});
                final HttpResponse newEsmLogin = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, newLoginData, null, loginUri);
                logger.info("New ESM Login ResponseCode : {}", newEsmLogin.getResponseCode().getCode());
                CommonUtils.sleep(1);
                if (!EnmApplication.acceptableResponse.contains(newEsmLogin.getResponseCode())) {
                    throw new EnmException("New ESM Login failed ... after password change!");
                }
            }

            //logout
            final HttpResponse newEsmLogout = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, logoutUri);
            logger.info("New ESM Logout ResponseCode : {}", newEsmLogout.getResponseCode().getCode());
            CommonUtils.sleep(1);
            if (!EnmApplication.acceptableResponse.contains(newEsmLogout.getResponseCode())) {
                throw new EnmException("ESM Logout failed.");
            }
            readyToVerify = true;
        } catch (final Exception e) {
            logger.error("Message : {}", e.getMessage());
            readyToVerify = false;
            HighAvailabilityTestCase.multiMapErrorCollection.put(HAPropertiesReader.CNOMSYSTEMMONITOR, "EnmSystemMonitor (cnom) failed in prepare phase: " + e.getMessage());
            HighAvailabilityTestCase.appsFailedToStart.add(new FunctionalAreaKey(CnomSystemMonitor.class.getSimpleName(), true));
        }
    }

    @Override
    public void verify() {

        if (readyToVerify) {
            try {
                final List<String[]> newLoginData = new ArrayList<>();
                newLoginData.add(new String[]{"username", "sec_admin"});
                newLoginData.add(new String[]{"password", defaultPassword});
                final HttpResponse newEsmLogin = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, newLoginData, null, loginUri);
                logger.info("ESM Login ResponseCode : {}", newEsmLogin.getResponseCode().getCode());
                if (!EnmApplication.acceptableResponse.contains(newEsmLogin.getResponseCode())) {
                    logger.info("ESM Login body : {}", newEsmLogin.getBody());
                    throw new EnmException("ESM Login failed.");
                }
                CommonUtils.sleep(1);
                //get all users data
                final HttpResponse esmGetUserData = httpRestServiceClient.sendGetRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, usersUri);
                logger.info("ESM esmGetUserData ResponseCode : {}", esmGetUserData.getResponseCode().getCode());
                if (!EnmApplication.acceptableResponse.contains(esmGetUserData.getResponseCode())) {
                    logger.info("CESM esmGetUserData body : {}", esmGetUserData.getBody());
                    throw new EnmException("ESM GetUsers Data failed.");
                }
                CommonUtils.sleep(1);

                //Get variables
                final HttpResponse getVariables = httpRestServiceClient.sendGetRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, variableUri);
                logger.info("getVariables ResponseCode : {}", getVariables.getResponseCode().getCode());
                if (getVariables.getBody().contains(nameSpace)) {
                    logger.info("ESM verified successfully!");
                } else {
                    logger.info("getVariables response body : {}", getVariables.getBody());
                    throw new EnmException("Failed to get ESM namespace!");
                }
                CommonUtils.sleep(1);
                //logout
                final HttpResponse esmLogout = httpRestServiceClient.sendPostRequest(20, null, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, logoutUri);
                logger.info("ESM Logout ResponseCode : {}", esmLogout.getResponseCode().getCode());
                if (!EnmApplication.acceptableResponse.contains(esmLogout.getResponseCode())) {
                    logger.info("ESM Logout body : {}", esmLogout.getBody());
                    throw new EnmException("ESM Logout failed.");
                }
                CommonUtils.sleep(1);
                setFirstTimeFail(true);
                stopAppDownTime();
                stopAppTimeoutDownTime();

            } catch (IllegalStateException e) {
                logger.error("Message : {}", e.getMessage());
                stopAppDownTime();
                if (isFirstTimeFail()) {
                    setFirstTimeFail(false);
                } else {
                    startAppTimeoutDowntime();
                }
            } catch (Exception e) {
                logger.error("Message : {}", e.getMessage());
                stopAppTimeoutDownTime();
                if (isFirstTimeFail()) {
                    setFirstTimeFail(false);
                } else {
                    startAppDownTime();
                }
            }
        }
    }

    @Override
    public void cleanup() throws EnmException {
        // Do nothing
    }

}
