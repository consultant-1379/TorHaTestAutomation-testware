package com.ericsson.nms.rv.core.nbi.error;

public class NbiException extends Exception {

    private final ErrorType errorType;

    public NbiException(final String message, final ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
