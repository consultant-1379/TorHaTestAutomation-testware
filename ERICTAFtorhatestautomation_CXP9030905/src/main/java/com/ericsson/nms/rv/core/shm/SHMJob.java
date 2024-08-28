package com.ericsson.nms.rv.core.shm;

class SHMJob {

    private String name;
    private String jobId;
    private String jobStatus;
    private String jobResult;

    SHMJob(final String name, final String jobId, final String jobStatus, final String jobResult) {
        this.name = name;
        this.jobId = jobId;
        this.jobStatus = jobStatus;
        this.jobResult = jobResult;
    }

    String getName() {
        return name;
    }

    String getJobId() {
        return jobId;
    }

    String getJobStatus() {
        return jobStatus;
    }

    String getJobResult() {
        return jobResult;
    }

    @Override
    public String toString() {
        return "SHMJob{" +
                "name='" + name + '\'' +
                ", jobId='" + jobId + '\'' +
                ", jobResult='" + jobResult + '\'' +
                ", jobStatus='" + jobStatus + '\'' +
                '}';
    }
}
