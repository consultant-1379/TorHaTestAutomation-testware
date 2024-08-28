/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.sut.test.cases;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;

public class CustomListener extends TestListenerAdapter {

    private static final Logger logger = LogManager.getLogger(CustomListener.class);

    @Override
    public void onConfigurationFailure(final ITestResult itr) {
        super.onConfigurationFailure(itr);
        logger.error(getStackTrace(itr.getThrowable()));
    }

    @Override
    public void onTestSkipped(final ITestResult tr) {
        super.onTestSkipped(tr);
        logger.error(getStackTrace(tr.getThrowable()));
    }

    private String getStackTrace(final Throwable aThrowable) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        return result.toString();
    }
}
