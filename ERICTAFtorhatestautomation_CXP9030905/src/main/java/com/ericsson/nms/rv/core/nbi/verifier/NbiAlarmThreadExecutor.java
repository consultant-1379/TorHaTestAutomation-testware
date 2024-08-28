package com.ericsson.nms.rv.core.nbi.verifier;

import com.ericsson.nms.rv.core.nbi.NbiFlow;
import com.ericsson.nms.rv.core.node.Node;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.nms.rv.core.util.HaTimeoutException;
import com.ericsson.oss.testware.availability.common.exception.EnmException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.List;

public class NbiAlarmThreadExecutor implements Runnable {

    private static final Logger logger = LogManager.getLogger(NbiAlarmThreadExecutor.class);
    private final NbiFlow nbiFlow;
    final List<Node> nbiNodesList;
    private Thread worker;
    private boolean completed;
    private boolean timedOut;
    private boolean errorOccurance;
    private long threadId;

    public boolean isCompleted() {
        return completed;
    }

    public boolean isErrorOcurred() {
        return errorOccurance;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public long getID() {
        return threadId;
    }

    NbiAlarmThreadExecutor(List<Node> nbiNodesList, NbiFlow nbiFlow) {
        this.nbiNodesList = nbiNodesList;
        this.nbiFlow = nbiFlow;
    }

    public void execute() {
        worker = new Thread(this);
        worker.start();
        threadId = worker.getId();
    }

    @Override
    public void run() {
        try {
            logger.info("ThreadId: {} Entered into the NbiAlarmThread....", threadId);
            executeNbiCurrentFlow(threadId);
            completed = true;
        } catch (EnmException e) {
            logger.error("ThreadId: {} Error occured in NBIAlarmThread : {}", threadId, e);
            errorOccurance = true;
        } catch (HaTimeoutException e) {
            logger.error("ThreadId: {} NBIAlarmThread timed out : {}", threadId, e);
            timedOut = true;
        } catch (Exception e) {
            logger.error("ThreadId: {} NBIAlarmThread failed : {}", threadId, e);
            errorOccurance = true;
        } finally {
            CommonUtils.sleep(1);
            Thread.currentThread().stop();
        }

    }

    private void executeNbiCurrentFlow(long threadId) throws EnmException, HaTimeoutException{
            nbiFlow.execute(threadId);
    }

}