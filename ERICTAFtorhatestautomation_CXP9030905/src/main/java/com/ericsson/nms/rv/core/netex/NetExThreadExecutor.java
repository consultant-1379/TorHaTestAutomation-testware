package com.ericsson.nms.rv.core.netex;

import static com.ericsson.nms.rv.core.netex.NetworkExplorer.netExIgnoreDTMap;

import java.util.Date;
import java.util.List;

import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.nms.rv.core.HAPropertiesReader;
import com.ericsson.nms.rv.core.restclient.HttpRestServiceClient;
import com.ericsson.nms.rv.taf.tools.Constants;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class NetExThreadExecutor implements Runnable {
    private static final Logger logger = LogManager.getLogger(NetExThreadExecutor.class);
    private Thread worker;
    private int timeout;
    private HttpResponse response;
    private String contextType;
    private String[] header;
    private List<String[]> body;
    private String[] queryParam;
    private String uri;
    private HttpRestServiceClient httpRestClient;

    public NetExThreadExecutor(int timeout, String contextType, String[] header, List<String[]> body, String[] queryParam, String uri, HttpRestServiceClient httpRestClient) {
        this.timeout = timeout;
        this.contextType = contextType;
        this.header = header;
        this.body = body;
        this.queryParam = queryParam;
        this.uri = uri;
        this.httpRestClient = httpRestClient;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public long getID() {
        return worker.getId();
    }

    public void execute() {
        worker = new Thread(this);
        worker.start();
    }

    public void run() {
        final long startTime = System.currentTimeMillis();
        final Date startDate = new Date(startTime);
        try {
            response = httpRestClient.sendGetRequest(timeout, contextType, header,body, queryParam, uri);
            final long respTime = response.getResponseTimeMillis();
            if (response != null && EnmApplication.acceptableResponse.contains(response.getResponseCode())) {
                if((respTime > 20 * Constants.TEN_EXP_3) && (respTime < 120 * Constants.TEN_EXP_3)) {
                    logger.info("NetEx ThreadId : {}, ResponseTimeinMilliSec: {}, ResponseCode: {}", worker.getId(), respTime, response.getResponseCode().getCode());
                    logger.info("Delayed Response time: {}", respTime);
                    final Date endDate = new Date(startTime + respTime);
                    logger.info("Updating map netExIgnoreDTMap startTime: {}, endTime: {}", startDate.toString(), endDate.toString());
                    netExIgnoreDTMap.put(startDate, endDate);
                    HAPropertiesReader.ignoreDTMap.put(HAPropertiesReader.NETWORKEXPLORER, netExIgnoreDTMap);
                }
            }
        } catch (final IllegalStateException e) {
            //Ignore as DT already started!
            logger.info("Thread: {} Netex request is timed out after {}s", getID(), timeout);
        } finally {
            Thread.currentThread().stop();
        }
    }

}
