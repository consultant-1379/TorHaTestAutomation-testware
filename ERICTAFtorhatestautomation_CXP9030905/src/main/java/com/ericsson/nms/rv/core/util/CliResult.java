package com.ericsson.nms.rv.core.util;


import com.ericsson.de.tools.cli.CliCommandResult;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CliResult {
    private static final Logger logger = LogManager.getLogger(CliResult.class);
    private final String output;
    private final int exitCode;
    private final long executionTime;

    CliResult(CliCommandResult result) {
        this.output = processStdOut(result.getOutput());
        this.exitCode = result.getExitCode();
        this.executionTime = result.getExecutionTime();
    }

    CliResult(String output, int exitCode, long executionTime) {
        this.output = output;
        this.exitCode = exitCode;
        this.executionTime = executionTime;
    }

    private String processStdOut(final String output) {
        StringBuilder sb = new StringBuilder();
        if (output.contains("exitcode")) {
            logger.info("Output with exitcode : {}", output);
            final String[] list = output.split("\n");
            int count = 0;
            for (final String line : list) {
                count++;
                if (line.contains("exitcode") || (count == 1 && line.contains("PS1"))) {
                    continue;
                } else {
                    sb.append(line);
                    sb.append("\n");
                }
            }
            if ( sb.length() > 0 && Character.toString(sb.charAt(sb.length() - 1)).equals("\n")) {
                sb.deleteCharAt(sb.length() - 1);
            }
            logger.info("Clean Output : {}", sb.toString());
            return sb.toString();
        } else {
            return output;
        }
    }

    public String getOutput() {
        return this.output;
    }

    public int getExitCode() {
        return this.exitCode;
    }

    public boolean isSuccess() {
        return this.exitCode == 0;
    }

    public long getExecutionTime() {
        return this.executionTime;
    }

    public String toString() {
        return "CliResult {output='" + this.output + '\'' + ", exitCode=" + this.exitCode + ", executionTime=" + this.executionTime + '}';
    }
}

