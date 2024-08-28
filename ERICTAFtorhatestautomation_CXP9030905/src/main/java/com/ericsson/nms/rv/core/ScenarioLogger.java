package com.ericsson.nms.rv.core;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.TafTestContext;
import com.ericsson.cifwk.taf.scenario.TestScenario;
import com.ericsson.cifwk.taf.scenario.TestStepFlow;
import com.ericsson.cifwk.taf.scenario.TestStepInvocation;
import com.ericsson.cifwk.taf.scenario.api.ScenarioExceptionHandler;
import com.ericsson.cifwk.taf.scenario.api.ScenarioListener;
import com.ericsson.cifwk.taf.scenario.impl.LoggingScenarioListener;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.truth.Truth;

/**
 * Class allowing to log and summarize execution of scenario.
 * All test steps are executed and amount of com.ericsson.oss.testware.nodeintegration.flows, test steps and failures are logged.
 * If any test step fails, AssertionError is thrown
 * <br>
 * Example usage:
 * <pre>
 * ScenarioLogger logger = new ScenarioLogger();
 * TestScenario scenario scenario = scenario("....")
 *    ....
 *    .withTestStepExceptionHandler(logger.exceptionHandler())
 *    .build();
 * runner = runner()
 *          .withListener(logger)
 *          .build();
 * runner.start(scenario);
 * <pre>
 */
public class ScenarioLogger implements ScenarioListener {

    private static final String SUCCESS_TOKEN = " SUCCESS";
    private static final String FAILURE_TOKEN = "FAILURE";
    private static final Logger LOGGER = LogManager.getLogger(ScenarioLogger.class);
    private final Map<Integer, Stack<String>> invocations = Maps.newConcurrentMap();
    private final ScenarioListener logScenarioListener = new LoggingScenarioListener();
    private final AtomicInteger steps = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicInteger flows = new AtomicInteger();
    private final List<String> failMessages = Lists.newCopyOnWriteArrayList();
    private final ThreadLocal<String> testStepData = new InheritableThreadLocal<>();
    private ExceptionHandler ehInstance;

    public void clean() {
        steps.set(0);
        failures.set(0);
        flows.set(0);
        invocations.clear();
        failMessages.clear();
    }

    @Override
    public void onFlowFinished(final TestStepFlow arg0) {
        logScenarioListener.onFlowFinished(arg0);
        flows.getAndIncrement();
    }

    @Override
    public void onFlowStarted(final TestStepFlow arg0) {
        logScenarioListener.onFlowStarted(arg0);
    }

    @Override
    public void onScenarioFinished(final TestScenario arg0) {
        logScenarioListener.onScenarioFinished(arg0);

        for (final Entry<Integer, Stack<String>> entries : invocations.entrySet()) {
            entries.getValue().forEach(LOGGER::info);
        }
        LOGGER.info("\n=================================================================\n" +
                "Executed Flows: {}, Steps total: {}, Failed Steps: {}" +
                "\n=================================================================", flows.get(), steps.get(), failures.get());
    }

    @Override
    public void onScenarioStarted(final TestScenario arg0) {
        logScenarioListener.onScenarioStarted(arg0);

    }

    public ExceptionHandler exceptionHandler() {
        if (ehInstance == null) {
            ehInstance = new ExceptionHandler();
        }
        return ehInstance;
    }

    @Override
    public void onTestStepFinished(final TestStepInvocation testStep) {
        final int vuser = TafTestContext.getContext().getVUser();
        Stack<String> inv = invocations.get(vuser);
        if (inv == null) {
            inv = new Stack<>();
            invocations.put(vuser, inv);
        }
        inv.push(String.format("%s: VUser %s-%s with %s ", SUCCESS_TOKEN, vuser, testStep.getName(), testStepData.get()));
        steps.getAndIncrement();
        logScenarioListener.onTestStepFinished(testStep);
    }

    @Override
    public void onTestStepStarted(final TestStepInvocation arg0, final Object[] arg1) {
        final String argumentString = Joiner.on("; ").useForNull("null").join(arg1);
        testStepData.set(argumentString);
        logScenarioListener.onTestStepStarted(arg0, arg1);
    }

    public void verify() {
        Truth.assert_().withMessage("FAIL: " + failures.get() + " out of " + steps.get() + " test steps failed").that(failures.get()).isEqualTo(0);
    }

    private class ExceptionHandler implements ScenarioExceptionHandler {
        @Override
        public Outcome onException(final Throwable exception) {
            failures.getAndIncrement();
            final Stack<String> msgs = invocations.get(TafTestContext.getContext().getVUser());
            if (msgs != null) {
                String msg = msgs.pop();
                msg = String.format("%s with exception %s", msg.replace(SUCCESS_TOKEN, FAILURE_TOKEN), exception.getCause());
                msgs.push(msg);
                LogManager.getLogger(LoggingScenarioListener.class).info(msg);
                failMessages.add(msg);
            }
            return Outcome.PROPAGATE_EXCEPTION;
        }
    }

}
