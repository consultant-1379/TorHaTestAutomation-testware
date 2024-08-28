package com.ericsson.nms.rv.core.system;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.nms.rv.core.EnmApplication;
import com.ericsson.oss.testware.availability.common.exception.EnmException;

public class SystemVerifier extends EnmApplication implements SystemStatus {

    private static final Logger logger = LogManager.getLogger(SystemVerifier.class);
    private final List<SystemObserver> observerList;
    private volatile AtomicBoolean isSSOAvailable = new AtomicBoolean(true);

    public SystemVerifier(final List<SystemObserver> observerList) {
        initDownTimerHandler(this);
        initFirstTimeFail();
        this.observerList = observerList;
    }

    @Override
    public void verify() {
        try {
            super.login();
            isSSOAvailable.set(true);
            stopSystemDownTime();
            setFirstTimeFail(true);
            sleep(2L);
        } catch (final EnmException e) {
            isSSOAvailable.set(false);
            if (isFirstTimeFail()) {
                setFirstTimeFail(false);
            } else {
                startSystemDownTime();
            }
            notifyAllSSODown();
            logger.error("Login has failed, sso service could be down, message: ", e);
        } finally {
            if (islogoutNeeded) {
                try {
                    super.logout();
                    isSSOAvailable.set(true);
                    stopSystemDownTime();
                    setFirstTimeFail(true);
                } catch (final EnmException e) {
                    isSSOAvailable.set(false);
                    if (isFirstTimeFail()) {
                        setFirstTimeFail(false);
                    } else {
                        startSystemDownTime();
                    }
                    notifyAllSSODown();
                    logger.error("Logout has failed, sso service could be down, message: ", e);
                }
            }
        }
    }

    @Override
    public void login() {
        //do nothing
    }

    @Override
    public void logout() {
        //do nothing
    }

    @Override
    public boolean isSSOAvailable() {
        return isSSOAvailable.get();
    }

    private void notifyAllSSODown() {
        observerList.parallelStream().forEach(SystemObserver::doLogin);
    }

    @Override
    public void doLogin() {
        //do nothing
    }
}
