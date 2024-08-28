package com.ericsson.nms.rv.core.netsimhandler;

public class HaNetsimHandlerException extends Exception {
    HaNetsimHandlerException(final String msg) {
        super(msg);
    }

    HaNetsimHandlerException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
