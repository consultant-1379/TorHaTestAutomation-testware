package com.ericsson.nms.rv.core.amos.operators.enm;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class LoginLogoutOperator {

    private static final String VALID_LOGIN = "0";

    private static final Logger logger = LogManager.getLogger(LoginLogoutOperator.class);

    public boolean login(final CloseableHttpClient httpTool, final String username, final String password,
                         final String enmHost) {

        logger.info("httpToolRef:{}", httpTool);

        final HttpUriRequest login;

        try {
            login = RequestBuilder.post().setUri(new URI("https://" + enmHost + "/login"))
                    .addParameter("IDToken1", username).addParameter("IDToken2", password).build();

            CloseableHttpResponse response = httpTool.execute(login);

            String authErrorCode;
            if (response != null) {
                logger.info("Response status line {}", response.getStatusLine());
            } else {
                logger.info("Response is null");
                return false;
            }

            final Header authErrorCodeHeader = response.getFirstHeader("X-AuthErrorCode");
            if (authErrorCodeHeader != null) {
                authErrorCode = authErrorCodeHeader.getValue();
            } else {
                logger.info("X-AuthErrorCode header is null");
                return false;
            }

            logger.info("Original status code {}, username {}, password {}", authErrorCode, username, password);

            logger.info("login. X-AuthErrorCode:{}", authErrorCode);

            if (!VALID_LOGIN.equals(authErrorCode)) {
                HttpEntity responseEntity = response.getEntity();

                String responseBody = "";

                if (responseEntity != null) {
                    responseBody = EntityUtils.toString(responseEntity);
                }

                logger.error("Invalid Credentials. Authentication Error Code: {} \nResponse Body: {},\nHttp Code: {}",
                        authErrorCode, responseBody, response.getStatusLine().getStatusCode());
                if (responseBody.contains("OpenAM (Authentication Failed)")) {

                    response = httpTool.execute(login);
                    responseEntity = response.getEntity();

                    responseBody = EntityUtils.toString(responseEntity);

                    logger.error(
                            "Response after second chance. Authentication Error Code: {} \nResponse Body: {},\nHttp Code: {}",
                            response.getFirstHeader("X-AuthErrorCode"), responseBody,
                            response.getStatusLine().getStatusCode());
                }

                return false;
            }

        } catch (final URISyntaxException | IOException e) {
            logger.error(e.getMessage(), e);
            return false;

        }

        return true;
    }
}
