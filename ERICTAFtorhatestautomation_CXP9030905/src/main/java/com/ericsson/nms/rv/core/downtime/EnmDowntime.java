package com.ericsson.nms.rv.core.downtime;

import com.ericsson.nms.rv.core.EnmApplication;

public class EnmDowntime {
    private final EnmApplication enmApplication;
    private Long applicationDowntime;
    private Long applicationTimeoutDowntime;

    /**
     * @param enmApplication
     */
    public EnmDowntime(final EnmApplication enmApplication) {
        this.enmApplication = enmApplication;
    }

    public Long getApplicationDowntime() {
        return applicationDowntime;
    }

    public Long getApplicationTimeoutDowntime() {
        return applicationTimeoutDowntime;
    }

    public void setApplicationDowntime(final Long applicationDowntime) {
        this.applicationDowntime = applicationDowntime;
    }

    public void setApplicationTimeoutDowntime(final Long applicationTimeoutDowntime) {
        this.applicationTimeoutDowntime = applicationTimeoutDowntime;
    }

    /**
     * @return the enmApplication
     */
    public EnmApplication getEnmApplication() {
        return enmApplication;
    }

}
