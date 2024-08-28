package com.ericsson.nms.rv.core;


public class EnmSystemException extends Exception {

    public EnmSystemException(final String msg) {
        super(msg);
    }

    public EnmSystemException(final String msg, final Exception e) {
        super(msg, e);
    }
}
