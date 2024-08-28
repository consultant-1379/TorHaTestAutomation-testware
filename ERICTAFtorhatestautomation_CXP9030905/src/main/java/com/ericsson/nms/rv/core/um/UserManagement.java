package com.ericsson.nms.rv.core.um;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.oss.testware.availability.common.exception.EnmErrorType;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.constants.ContentType;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.system.SystemVerifier;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

public final class UserManagement extends EnmApplication {

    private static final Logger logger = LogManager.getLogger(UserManagement.class);
    private static final String NAME = "HATestName";
    private static final String PASSWORD = "Testpassw0rd";
    private static final String FAILED_TO___USER = "Failed to %s user: %s";
    private static final String OSS_IDM_USERMANAGEMENT_USERS = "/oss/idm/usermanagement/";
    private static final String USERNAME_CANNOT_BE_NULL = "Username cannot be NULL!";
    private static final long timeToWait = Constants.TEN_EXP_9 * Constants.Time.ONE_MINUTE_IN_SECONDS * 5;

    public UserManagement(final SystemVerifier systemStatus) {
        initDownTimerHandler(systemStatus);
        initFirstTimeFail();
    }

    @Override
    public void verify() throws EnmException, HaTimeoutException {

        final String msg = "Sucessfully {} user: {}";
        final String id = createUser();
        logger.info(msg, "created", id);
        activateUser(id);
        logger.info(msg, "activated", id);
        long startTime = System.nanoTime();
        do {
            upPasswordAgeing(id);
            logger.info(msg, "modified", id);
            checkUser(id);
            logger.info(msg, "checked", id);
            sleep(2);
        } while(!EnmApplication.getSignal() && (System.nanoTime() - startTime) < timeToWait);
        deleteUser(id);
        logger.info(msg, "deleted", id);
    }

    private void upPasswordAgeing(final String id) throws EnmException, HaTimeoutException {
        if (id != null) {
            try (final InputStream inputStream = UserManagement.class.getResourceAsStream("/json/umUpdate.json")) {
                final Object parse = JSONValue.parse(inputStream);
                if (parse instanceof JSONObject) {
                    final JSONObject jsonObject = (JSONObject) parse;

                    logger.info("After update json its parameters are {}",jsonObject.toString());
                    final List<String[]> bodies = new ArrayList<>();
                    bodies.add(new String[]{jsonObject.toString()});
                    final HttpResponse response;
                    try {
                        response = getHttpRestServiceClient().sendPutRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, OSS_IDM_USERMANAGEMENT_USERS + "users/" + id + "/passwordAgeing");
                    } catch (final IllegalStateException e) {
                        throw new HaTimeoutException("UM: Timeout in updateuser!" + ": " + e.getMessage(), EnmErrorType.APPLICATION);
                    }
                    analyzeResponse(String.format(FAILED_TO___USER, "updateuser", id), response);
                }
            } catch (final IOException e) {
                throw new EnmException("Failed to read jsonFile, while updating upPasswordAgeing root cause: ", e);
            }
        } else {
            logger.warn(USERNAME_CANNOT_BE_NULL);
        }
    }


    private String createUser() throws EnmException, HaTimeoutException {
        final Random random = new Random(System.currentTimeMillis());
        final String username = NAME + random.nextLong();
        final JSONObject jsonObject = fillBody(username, false);
        final List<String[]> bodies = new ArrayList<>();
        if (jsonObject != null) {
            bodies.add(new String[]{jsonObject.toString()});
        }

        final HttpResponse response;
        try {
            response = getHttpRestServiceClient().sendPostRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, OSS_IDM_USERMANAGEMENT_USERS + "users/");
        } catch (final IllegalStateException e) {
            throw new HaTimeoutException("UM: Timeout in createUser!" + ": " + e.getMessage(), EnmErrorType.APPLICATION);
        }
        analyzeResponse(String.format(FAILED_TO___USER, "create", username), response);
        return getUserName(response.getBody());

    }

    private void activateUser(final String id) throws EnmException, HaTimeoutException {
        if (id != null) {
            final JSONObject jsonObject = fillBody(id, true);
            if (jsonObject != null) {
                final List<String[]> bodies = new ArrayList<>();
                bodies.add(new String[]{jsonObject.toString()});

                final HttpResponse response;
                try {
                    response = getHttpRestServiceClient().sendPutRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, bodies, null, OSS_IDM_USERMANAGEMENT_USERS + "users/" + id);
                } catch (IllegalStateException e) {
                    throw new HaTimeoutException("UM: Timeout in activateUser!" + ": " + e.getMessage(), EnmErrorType.APPLICATION);
                }
                analyzeResponse(String.format(FAILED_TO___USER, "activate", id), response);
            }
        } else {
            logger.warn(USERNAME_CANNOT_BE_NULL);
        }
    }

    private void checkUser(final String id) throws EnmException, HaTimeoutException {
        if (id != null) {
            final HttpResponse response;
            try {
                response = getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, OSS_IDM_USERMANAGEMENT_USERS + "users/" + id);
            } catch (final IllegalStateException e) {
                throw new HaTimeoutException("UM: Timeout in checkUser!" + ": " + e.getMessage(), EnmErrorType.APPLICATION);
            }
            analyzeResponse(String.format(FAILED_TO___USER, "check", id), response);
        } else {
            logger.warn(USERNAME_CANNOT_BE_NULL);
        }
    }

    private String deleteUser(final String id) throws EnmException, HaTimeoutException {
        if (id != null) {
            final HttpResponse response;
            try {
                response = getHttpRestServiceClient().sendDeleteRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{Header.ACCEPT.getValue(), ContentType.APPLICATION_JSON}, null, null, OSS_IDM_USERMANAGEMENT_USERS + "users/" + id);
            } catch (final IllegalStateException e) {
                throw new HaTimeoutException("UM: Timeout in deleteUser!" + ": " + e.getMessage(), EnmErrorType.APPLICATION);
            }
            analyzeResponse(String.format(FAILED_TO___USER, "delete", id), response);
        } else {
            logger.warn(USERNAME_CANNOT_BE_NULL);
        }
        return Constants.EMPTY_STRING;
    }

    private JSONObject fillBody(final String id, final boolean status) throws EnmException {
        JSONObject jsonObject = null;
        try (final InputStream inputStream = UserManagement.class.getResourceAsStream("/json/umCreateUser.json")) {
            final Object parse = JSONValue.parse(inputStream);
            if (parse instanceof JSONObject) {
                jsonObject = (JSONObject) parse;
                jsonObject.put("username", id);
                jsonObject.put("name", NAME);
                jsonObject.put("surname", "Last" + NAME);
                jsonObject.put("email", NAME + "@" + NAME + ".com");
                jsonObject.put("status", status ? "enabled" : "disabled");
                if (!status) {
                    jsonObject.put("password", PASSWORD);
                }
            }
        } catch (final IOException e) {
            throw new EnmException("Failed to read jsonFile, root cause: ", e);
        }
        return jsonObject;
    }

    @Override
    public void cleanup() throws EnmException{
        final List<String> allUsers = getAllUsers();
        int count = 0;
        if (!allUsers.isEmpty()) {
            for(final String user : allUsers) {
                if(user.contains(NAME)) {
                    try {
                        deleteUser(user);
                    } catch (final EnmException | HaTimeoutException e) {
                        count++;
                    }
                }
            }
        }
        if(count > 0) {
            throw new EnmException("Failed to cleanup.");
        }
    }

    private List<String> getAllUsers() {
        final List<String> list = new ArrayList<>();
        final HttpResponse response = getHttpRestServiceClient().sendGetRequest(TIMEOUT_REST, ContentType.APPLICATION_JSON, new String[]{"ContentType", ContentType.APPLICATION_JSON}, null, null, OSS_IDM_USERMANAGEMENT_USERS + "users/");
        final Object parse = JSONValue.parse(response.getBody());
        if (parse instanceof JSONArray) {
            ((JSONArray) parse).stream().collect(Collectors.toList()).forEach(o -> list.add((String) ((JSONObject) o).get("username")));
        }
        return list;
    }

    private String getUserName(final String stdOut) throws EnmException {
        final Object o = JSONValue.parse(stdOut);
        if (o instanceof JSONObject) {
            final JSONObject jsonObjectResponse = (JSONObject) o;
            return String.valueOf(jsonObjectResponse.get("username"));
        } else if (o instanceof JSONArray) {
            final JSONObject jsonObjectResponse = (JSONObject) ((JSONArray) o).get(0);
            return String.valueOf(jsonObjectResponse.get("username"));
        }
        return null;
    }
}
