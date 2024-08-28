package com.ericsson.nms.rv.core.restclient;

import java.util.List;

import com.ericsson.nms.rv.core.HAPropertiesReader;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.tools.http.HttpResponse;
import com.ericsson.cifwk.taf.tools.http.HttpTool;
import com.ericsson.cifwk.taf.tools.http.RequestBuilder;
import com.ericsson.de.tools.http.BasicHttpToolBuilder;
import com.ericsson.nms.rv.taf.tools.Constants;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;

public class HttpRestServiceClient {

    private static final Logger logger = LogManager.getLogger(HttpRestServiceClient.class);

    private final RequestBuilderFactory requestBuilderFactory;
    private HttpTool httpTool;

    public HttpRestServiceClient() {
        httpTool = getHttpToolInstance();
        requestBuilderFactory = new RequestBuilderFactory(httpTool);
    }

    public HttpRestServiceClient(final String host) {
        switch (host) {
            case "ESM":
                httpTool = getHttpToolInstanceForEsm();
                break;
            case "EXTERNAL":
                httpTool = getHttpToolInstanceForExternalVm();
                break;
            default:
                httpTool = getHttpToolInstance();
                break;
        }
        requestBuilderFactory = new RequestBuilderFactory(httpTool);
    }

    public HttpTool getHttpTool() {
        return httpTool;
    }

    public HttpResponse sendGetRequest(final int timeout,            //  0 - DEFAULT
                                       final String contextType,     //  one from enum ContentType
                                       final String[] header,        //  Header.ACCEPT, ContentType.APPLICATION_JSON
                                       final List<String[]> body,    //
                                       final String[] queryParam,    //  "searchQuery", query
                                       final String uri) {
        final RequestBuilder requestBuilder = createRequestBuilder(timeout, contextType, header, body, queryParam);
        return execute(requestBuilder, uri, Action.GET);
    }

    public HttpResponse sendPostRequest(final int timeout,            //  0 - DEFAULT
                                        final String contextType,     //  one from enum ContentType
                                        final String[] header,        //  Header.ACCEPT, ContentType.APPLICATION_JSON
                                        final List<String[]> body,    //
                                        final String[] queryParam,    //  "searchQuery", query
                                        final String uri) {
        final RequestBuilder requestBuilder = createRequestBuilder(timeout, contextType, header, body, queryParam);
        return execute(requestBuilder, uri, Action.POST);
    }

    public HttpResponse sendDeleteRequest(final int timeout,            //  0 - DEFAULT
                                          final String contextType,     //  one from enum ContentType
                                          final String[] header,        //  Header.ACCEPT, ContentType.APPLICATION_JSON
                                          final List<String[]> body,    //
                                          final String[] queryParam,    //  "searchQuery", query
                                          final String uri) {
        final RequestBuilder requestBuilder = createRequestBuilder(timeout, contextType, header, body, queryParam);
        return execute(requestBuilder, uri, Action.DELETE);
    }

    public HttpResponse sendHeadRequest(final int timeout,            //  0 - DEFAULT
                                        final String contextType,     //  one from enum ContentType
                                        final String[] header,        //  Header.ACCEPT, ContentType.APPLICATION_JSON
                                        final List<String[]> body,    //
                                        final String[] queryParam,    //  "searchQuery", query
                                        final String uri) {
        final RequestBuilder requestBuilder = createRequestBuilder(timeout, contextType, header, body, queryParam);
        return execute(requestBuilder, uri, Action.HEAD);
    }

    public HttpResponse sendPutRequest(final int timeout,            //  0 - DEFAULT
                                       final String contextType,     //  one from enum ContentType
                                       final String[] header,        //  Header.ACCEPT, ContentType.APPLICATION_JSON
                                       final List<String[]> body,    //
                                       final String[] queryParam,    //  "searchQuery", query
                                       final String uri) {
        final RequestBuilder requestBuilder = createRequestBuilder(timeout, contextType, header, body, queryParam);
        return execute(requestBuilder, uri, Action.PUT);
    }

    private RequestBuilder createRequestBuilder(final int timeout,            //  0 - DEFAULT
                                                final String contextType,     //  one from enum ContentType
                                                final String[] header,        //  Header.ACCEPT, ContentType.APPLICATION_JSON
                                                final List<String[]> body,    //
                                                final String[] queryParam) {
        return requestBuilderFactory.create(timeout, contextType, header, body, queryParam);
    }

    private HttpResponse execute(final RequestBuilder requestBuilder, final String uri, final Action action) {
        final HttpResponse httpResponse;
        final long startTime = System.nanoTime();
        switch (action) {
            case GET:
                httpResponse = requestBuilder.get(uri);
                break;
            case PUT:
                httpResponse = requestBuilder.put(uri);
                break;
            case HEAD:
                httpResponse = requestBuilder.head(uri);
                break;
            case POST:
                httpResponse = requestBuilder.post(uri);
                break;
            case DELETE:
                httpResponse = requestBuilder.delete(uri);
                break;
            default:
                throw new IllegalArgumentException("Unknown action");
        }
        logger.trace("execution time is : {}", (System.nanoTime() - startTime) / Constants.TEN_EXP_9);
        return httpResponse;
    }

    private HttpTool getHttpToolInstance() {
        if (httpTool == null) {
            if (HAPropertiesReader.isEnvCloudNative()) {
                httpTool = BasicHttpToolBuilder
                        .newBuilder(HAPropertiesReader.cloudIngressHost)
                        .withPort(443)
                        .withProtocol("https")
                        .useHttpsIfProvided(true)
                        .trustSslCertificates(true)
                        .followRedirect(true)
                        .build();
            } else {
                final Host apache = HostConfigurator.getApache();
                httpTool = BasicHttpToolBuilder
                        .newBuilder(apache.getIp())
                        .withPort(apache.getHttpsPort())
                        .withProtocol("https")
                        .useHttpsIfProvided(true)
                        .trustSslCertificates(true)
                        .followRedirect(true)
                        .build();
            }
        }

        return httpTool;
    }

    private HttpTool getHttpToolInstanceForEsm() {
        if (httpTool == null) {
            final List<Host> hostList = HostConfigurator.getAllHosts("esmon");
            if (!hostList.isEmpty()) {
                final Host esmHost = hostList.get(0);
                if (esmHost != null) {
                    logger.info("esmHost IP : {}", esmHost.getIp());
                    httpTool = BasicHttpToolBuilder
                            .newBuilder(esmHost.getIp())
                            .withPort(esmHost.getHttpsPort())
                            .withProtocol("https")
                            .useHttpsIfProvided(true)
                            .trustSslCertificates(true)
                            .followRedirect(true)
                            .build();
                }
            }
        }
        return httpTool;
    }

    private HttpTool getHttpToolInstanceForExternalVm() {
        if (httpTool == null) {
            final Host host = HAPropertiesReader.getExternalHost();
            if (host != null) {
                logger.info("EXTERNAL-HOST IP : {}", host.getIp());
                httpTool = BasicHttpToolBuilder
                        .newBuilder(host.getIp())
                        .withPort(8443)
                        .withProtocol("https")
                        .useHttpsIfProvided(true)
                        .trustSslCertificates(true)
                        .followRedirect(true)
                        .build();
            }
        }
        return httpTool;
    }

    public boolean close() {
        try {
            httpTool.close();
        } catch (Exception e) {
            logger.warn("Exception in closing httpTool : {}", e.getMessage());
            return false;
        }
        return true;
    }

    private enum Action {
        PUT, GET, HEAD, POST, DELETE
    }
}
