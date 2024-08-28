package com.ericsson.sut.test.cases.util;

public class FunctionalAreaKey {
    private final String className;
    private final boolean enable;

    public FunctionalAreaKey(final String className, final boolean enable) {
        this.className = className;
        this.enable = enable;
    }

    public String getClassName() {
        return className;
    }

    public boolean isEnable() {
        return enable;
    }

}
