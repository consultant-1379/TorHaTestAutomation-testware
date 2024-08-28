package com.ericsson.nms.rv.core.restclient;

import java.util.List;

import com.ericsson.cifwk.taf.tools.http.HttpTool;
import com.ericsson.cifwk.taf.tools.http.RequestBuilder;

class RequestBuilderFactory {

    private final HttpTool httpTool;

    public RequestBuilderFactory(final HttpTool httpTool) {
        this.httpTool = httpTool;
    }

    RequestBuilder create(final int timeout,                   //  0 - DEFAULT
                          final String contextType,     //  one from enum ContentType
                          final String[] header,        //  Header.ACCEPT, ContentType.APPLICATION_JSON
                          final List<String[]> body,    //
                          final String[] queryParam) {

        RequestBuilder requestBuilder = httpTool.request();

        if (timeout > 0) {
            requestBuilder = requestBuilder.timeout(timeout);
        }
        if (contextType != null) {
            requestBuilder = requestBuilder.contentType(contextType);
        }
        if (header != null) {
            for (int i = 0; i < header.length; i++) {
                requestBuilder = requestBuilder.header(header[i], header[++i]);
            }
        }
        if (body != null) {
            requestBuilder = addBody(body, requestBuilder);
        }
        if (queryParam != null) {
            requestBuilder = addQueryParam(queryParam, requestBuilder);
        }
        return requestBuilder;
    }

    private RequestBuilder addBody(final Iterable<String[]> body, final RequestBuilder requestBuilder) {
        RequestBuilder returnRequestBuilder = requestBuilder;
        for (final String[] arr : body) {
            switch (arr.length) {
                case 1:
                    returnRequestBuilder = returnRequestBuilder.body(arr[0]);
                    break;
                case 2:
                    returnRequestBuilder = returnRequestBuilder.body(arr[0], arr[1]);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown body's size!");
            }
        }
        return returnRequestBuilder;
    }

    private RequestBuilder addQueryParam(final String[] queryParam, final RequestBuilder requestBuilder) {
        final int length = queryParam.length;
        final String[] param = new String[length - 1];
        System.arraycopy(queryParam, 1, param, 0, length - 1);
        return requestBuilder.queryParam(queryParam[0], param);
    }
}
