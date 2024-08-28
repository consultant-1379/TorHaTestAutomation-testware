package com.ericsson.nms.rv.core.util;

import static com.ericsson.nms.rv.core.EnmApplication.TIMEOUT_REST;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.HAconstants;
import com.ericsson.nms.rv.core.um.UserManagement;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class AdminUserUtils {
    private static final Logger logger = LogManager.getLogger(AdminUserUtils.class);
    private static String enmFlag = "enm";
    private static final String OSS_IDM_USERMANAGEMENT_USERS = "/oss/idm/usermanagement/";
    private static final String DELETEUSER = "/oss/idm/usermanagement/users/";
    private static final Random random = new Random(System.currentTimeMillis());
    private static final String username = HAPropertiesReader.getHausername() + random.nextLong();
    private static final String user = username;
    private static final String PASSWORD = HAPropertiesReader.getHaPassword();
    public static String createFlag= "pass";
    public static String userErrorMessage ="";

    public static String getEnmFlag() {
        return enmFlag;
    }

    public static String getUser() {
        return user;
    }

    public static String getPassword() {
        return PASSWORD;
    }

    public static String createNewUser(final EnmApplication enmApplication, final String name, final boolean isLdap) throws EnmException {
        final HttpResponse response;
        final JSONObject jsonObject = fillBody(name, PASSWORD);
        final List<String[]> bodies = new ArrayList<>();
        enmApplication.login();
        logger.info("Json object for HA user creation is  {}", jsonObject.toString());
        if (jsonObject != null) {
            bodies.add(new String[]{jsonObject.toString()});
        }
        try {
            response = enmApplication.getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"Accept", ContentType.APPLICATION_JSON}, bodies, null, OSS_IDM_USERMANAGEMENT_USERS + "users/");
            if(response.getResponseCode().getCode() == 201){
                logger.info("Create new user rest call is success with code {}",response.getResponseCode().getCode());
                if (!isLdap) {
                    enmFlag = "ha";
                }
            } else {
                throw new EnmException("Create user response body is:" + response.getBody());
            }
        } catch (Exception e) {
            throw new EnmException("exception while creating Ha user used in ADU is:" + e.getMessage());
        }
        enmApplication.logout();
        CommonUtils.sleep(3);
        return getCreds(response.getBody());
    }

    private static String getCreds(final String stdOut) {
        final Object o = JSONValue.parse(stdOut);
        if (o instanceof JSONObject) {
            final JSONObject jsonObjectResponse = (JSONObject) o;
            return String.valueOf(jsonObjectResponse.get("username"));
        } else if (o instanceof JSONArray) {
            final JSONObject jsonObjectResponse = (JSONObject) ((JSONArray) o).get(0);
            logger.info("Username created is {}",jsonObjectResponse.get("username"));
            return String.valueOf(jsonObjectResponse.get("username"));
        }
        return HAconstants.EMPTY_STRING;
    }

    private static JSONObject fillBody(String username, final String password) throws EnmException {
        JSONObject jsonObject = null;
        try (final InputStream inputStream = UserManagement.class.getResourceAsStream("/json/hacreateUser.json")) {
            final Object parse = JSONValue.parse(inputStream);
            if (parse instanceof JSONObject) {
                jsonObject = (JSONObject) parse;
                jsonObject.put("username", username);
                jsonObject.put("password", password);
                jsonObject.put("name", username);
                jsonObject.put("surname", "Last" + username);
                jsonObject.put("email", username + "@" + username + ".com");
                jsonObject.put("status", "enabled");
            }
        } catch (final IOException e) {
            throw new EnmException("Failed to read jsonFile, root cause: ", e);
        }
        logger.info("After filling json its parameters are {}",jsonObject);
        return jsonObject;
    }

    public static void deleteUser(final EnmApplication enmapp, final String user, final boolean isLdap) throws EnmException {
        enmFlag ="enm";
        enmapp.login();
        final HttpResponse delResponse;
        logger.info("Username to be deleted is {}",user);

        try {
            delResponse = enmapp.getHttpRestServiceClient().sendDeleteRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON,
                    new String[]{"ACCEPT", ContentType.APPLICATION_JSON}, null, null, DELETEUSER + user);
            if (delResponse.getResponseCode().getCode() == 204) {
                if (!isLdap) {
                    enmFlag = "ha";
                }
                logger.info("Deleted the created HA user during ADU");

            } else {
                throw new EnmException("Deleting user response body is:" + delResponse.getBody());
            }
        } catch (Exception e) {
            throw new EnmException("exception while deleting Ha user used in ADU is" + e.getMessage());
        }
    }

    public static void haCreateAdminUser(final UserManagement userManagement) {
        updateSsoSessionTime(userManagement, "1200", "120");
        int count = 0;
        String userAdmin;
        while (count < 5) {
            try {
                userAdmin = AdminUserUtils.createNewUser(userManagement, username, false);
                if(userAdmin != null && !userAdmin.isEmpty()) {
                    logger.info("Ha test user name is {} ", userAdmin);
                    createFlag = "pass";
                    break;
                } else {
                    logger.info("Ha test user name is empty.");
                }
            } catch (final EnmException e) {
                count = count + 1;
            }
        }
        logger.info("Retried {} times for creating the new user",count);
        if (count == 5) {
            createFlag = "fail";
            logger.info("Creation of user is failed for {} times",count);
            userErrorMessage = "Separate Admin user creation failed at initial stage. SO ADU used default Administrator user for Login to ENM during ADU. No impact to ADU results. Raise a Support ticket on Bravo to analyze it further with Team- \"Despicable US\".";
        }
    }

    public static void haUserCleanup(final UserManagement userManagement) {
        updateSsoSessionTime(userManagement, "600", "60");
        if(enmFlag.equalsIgnoreCase("ha")) {
            int delCounter = 0;
            while (delCounter < 5) {
                try {
                    deleteUser(userManagement, user, false);
                    logger.info("Ha test user name is deleted");
                    break;
                } catch (EnmException e) {
                    delCounter = delCounter + 1;
                }
            }
            logger.info("Retried {} times for deletion of  user",delCounter);
            if (delCounter == 5) {
                logger.info("Failed in deleting the user {} used for login throughout the ADU.Please Manualy delete", user);
            }
        }
    }

    private static void updateSsoSessionTime(final UserManagement userManagement, final String sessionTimeout, final String idleSessionTimeout) {
        if (HAPropertiesReader.getTestType().equalsIgnoreCase("Regression") && HAPropertiesReader.getTestSuitName().contains("KillJbosses")) {
            logger.info("Updating session timeout to: {}/{} ...", sessionTimeout, idleSessionTimeout);
            try {
                userManagement.login();
                final HttpResponse getResponse = userManagement.getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"Accept", ContentType.APPLICATION_JSON}, null, null, "/oss/sso/utilities/config");
                logger.info("GET session config response: {}/{}", getResponse.getResponseCode(), getResponse.getBody());
                JSONObject obj = (JSONObject) JSONValue.parse(getResponse.getBody());
                final String epoch = (String) obj.get("timestamp");
                logger.info("epoch :{}", epoch);

                final List<String[]> bodies = new ArrayList<>();
                bodies.add(new String[]{"{\"timestamp\":\"" + epoch + "\", \"session_timeout\":\"" + sessionTimeout + "\", \"idle_session_timeout\":\"" + idleSessionTimeout + "\"}"});
                logger.info("bodies : {}", bodies);
                final HttpResponse response = userManagement.getHttpRestServiceClient().sendPutRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"Accept", ContentType.APPLICATION_JSON}, bodies, null, "/oss/sso/utilities/config");
                logger.info("Update session timeout config response: {}/{}", response.getResponseCode(), response.getBody());
                userManagement.logout();
                CommonUtils.sleep(5);
            } catch (final Exception e) {
                logger.warn("Failed to Update session timeout config to : {}/{}", sessionTimeout, idleSessionTimeout);
            }
        }
    }
}
