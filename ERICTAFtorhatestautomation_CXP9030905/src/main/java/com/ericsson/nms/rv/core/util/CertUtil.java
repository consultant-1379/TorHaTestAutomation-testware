package com.ericsson.nms.rv.core.util;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.ericsson.cifwk.taf.data.DataHandler;
import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import com.google.common.base.Preconditions;


public class CertUtil {

    private static final Logger logger = LogManager.getLogger(CertUtil.class);
    private static String ditPrivateKey = "";

    public static Path getPemFilePath() {
        final String hostname = getHostnameOfDeployment();
        final String contents = getPrivateKey();    //Return empty when no certificate found
        final File pemFile = writePrivateKeyToFile(hostname, contents);
        return pemFile.toPath().toAbsolutePath();
    }

    private static File writePrivateKeyToFile(final String hostname, final String contents) {
        final File pemFile = new File(hostname + ".pem");
        if (pemFile.exists()) {
            //Do nothing!
        } else {
            try (BufferedWriter pemFileWriter = Files.newBufferedWriter(pemFile.toPath(), Charset.defaultCharset())) {
                pemFileWriter.write(contents);
                logger.info("Wrote private key to {}", pemFile.getAbsolutePath());
            } catch (final IOException e) {
                logger.error("Failed to write private key contents to file", e);
            }
        }
        return pemFile;
    }

    private static String getPrivateKey() {
        final String cert = getCertificate();
        if (!cert.isEmpty()) {
            return cert;
        } else {
            logger.warn("Certificate is not found.");
        }
        return cert;
    }

    private static String getHostnameOfDeployment() {
        Object hostname = DataHandler.getAttribute("taf.config.dit.deployment.name");
        if (hostname == null) {
            logger.warn("Could not find property taf.config.dit.deployment.name attempting to get hostname from host object");
            hostname = getHostnameFromApacheHostObject();
        }
        Preconditions.checkNotNull(hostname, "Could not get hostname of deployment");
        logger.info("Hostname of deployment was found to be: {}", hostname.toString());
        return hostname.toString();
    }

    private static String getHostnameFromApacheHostObject() {
        final Host apache = HostConfigurator.getApache();
        final String url = apache.getIp();
        Preconditions.checkNotNull(url, "No url in the apache host, %s", apache);
        return url.split("[.]")[0];
    }

    public static String getCertificate() {
        if(!ditPrivateKey.isEmpty()) {
            return ditPrivateKey;
        }
        final Object tafConfigDitDeploymentName = DataHandler.getAttribute("taf.config.dit.deployment.name");
        if (tafConfigDitDeploymentName != null) {
            logger.info("taf.config.dit.deployment.name\t=\t{}", tafConfigDitDeploymentName);
        }
        HttpURLConnection conn = null;
        JsonElement privateKey = null;
        try {
            URL url = new URL("https://atvdit.athtem.eei.ericsson.se/api/deployments/?q=name=" + tafConfigDitDeploymentName + "&fields=enm");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            final Gson gson = new GsonBuilder().create();
            if (conn.getResponseCode() != 200) {
                logger.warn("Failed : HTTP error code : for PEM KEY {}", conn.getResponseCode());
            }
            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            String output;
            while ((output = br.readLine()) != null) {
                logger.info("Output from Server {}", output);
                JSONArray array = (JSONArray) JSONValue.parse(output);
                final JSONObject obj = (JSONObject) array.get(0);
                final JsonObject jobArray = gson.fromJson((obj).toJSONString(), JsonObject.class);
                final JsonObject jobInfo = jobArray.getAsJsonObject();
                final JsonElement enmKey = jobInfo.get("enm");
                final JsonObject pkInfo = enmKey.getAsJsonObject();
                privateKey = pkInfo.get("private_key");
            }
            conn.disconnect();
        } catch (MalformedURLException e) {
            logger.info("Pem key MalformedURLException : {}", e.getMessage(), e);
        } catch (IOException e) {
            logger.info("Pem key IOException {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.info("Exception {}", e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            logger.info("Pem key URL conn disconnected");
        }
        if (privateKey != null) {
            ditPrivateKey = privateKey.getAsString();
            logger.info("ditPrivateKey : {}", ditPrivateKey);
        } else {
            logger.error("ditPrivateKey is null!");
        }
        return ditPrivateKey;
    }
}
