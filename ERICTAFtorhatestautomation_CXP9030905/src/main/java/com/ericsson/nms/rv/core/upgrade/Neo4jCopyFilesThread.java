package com.ericsson.nms.rv.core.upgrade;

import com.ericsson.nms.rv.core.HAPropertiesReader;

import com.ericsson.nms.rv.core.util.CertUtil;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.nio.charset.StandardCharsets;


public class Neo4jCopyFilesThread implements Runnable {
    private static final Logger logger = LogManager.getLogger(Neo4jCopyFilesThread.class);
    private Thread worker;
    private boolean isCompleted;

    public boolean isCompleted() {
        return isCompleted;
    }

    public Neo4jCopyFilesThread() {
    }

    public void execute() {
        this.worker = new Thread(this);
        logger.info("neo4j files started copying {}", this.worker.getId());
        this.worker.start();
    }

    void interruptThread() {
        this.worker.stop();
    }

    boolean threadState() {
        return this.worker.isAlive();
    }

    @Override
    public void run() {
        logger.info("Copy Neo4j files to Report ");
        for (int loopCount = 0; loopCount <= 2; loopCount++) {
            try {
                final String readFile = String.format("Eng-Neo4jReadOut-%d.csv", loopCount);
                downloadFileFromserver(readFile);
                final String writeFile = String.format("Eng-Neo4jWriteOut-%d.csv", loopCount);
                downloadFileFromserver(writeFile);
            } catch (final Exception e) {
                logger.warn("Upload file error: {}", e.getMessage());
            }
        }
        try {
            downloadFileFromserver("StartNeo4j.log");
        } catch (Exception e) {
            logger.warn("Error in Uploading StartNeo4j.log file : {}", e.getMessage());
        }
        this.isCompleted = true;
    }

    public void downloadFileFromserver(String readFile) {
        final String path = HAPropertiesReader.path + "/Neo4j-DT/";
        final JSch jsch = new JSch();

        try {
            logger.info("downloading file from IP: {}, File : {}", HAPropertiesReader.getMS().getIp(), readFile);
            if (HAPropertiesReader.isEnvCloud()) {
                jsch.addIdentity(CertUtil.getPemFilePath().toString());
            }
            final Session session = jsch.getSession(HAPropertiesReader.getMS().getUser(), HAPropertiesReader.getMS().getIp(), 22);
            session.setConfig("StrictHostKeyChecking", "no");
            if (!HAPropertiesReader.isEnvCloud()) {
                session.setPassword(HAPropertiesReader.getMS().getPass());
            }
            session.setTimeout( 600 *  Constants.TEN_EXP_3);  //time out in milli sec
            logger.info("connecting session with user {} :",session.getUserName());
            session.connect();
            final Channel channel = session.openChannel("sftp");
            channel.connect();
            final ChannelSftp sftpChannel = (ChannelSftp) channel;
            sftpChannel.cd(path);
            try {
                CommonUtils.txtFiles.put(readFile, IOUtils.toString(sftpChannel.get(readFile), StandardCharsets.UTF_8.name()));
            }
            catch(final Exception e){
                logger.info("disconnecting...");
                sftpChannel.disconnect();
                channel.disconnect();
                session.disconnect();
                throw e;
            }
            logger.info("disconnecting...");
            sftpChannel.disconnect();
            channel.disconnect();
            session.disconnect();
            logger.info("File uploaded successfully - " + readFile);
        } catch (final Exception e) {
            logger.warn("Upload file error: {}", e.getMessage());
        }
    }
}
