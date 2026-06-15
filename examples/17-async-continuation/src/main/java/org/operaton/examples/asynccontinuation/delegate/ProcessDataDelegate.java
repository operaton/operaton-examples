package org.operaton.examples.asynccontinuation.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component("processDataDelegate")
public class ProcessDataDelegate implements JavaDelegate {

    // Thread-safe counter: tracks how many times this delegate was called per process instance
    private static final ConcurrentHashMap<String, Integer> callCounts = new ConcurrentHashMap<>();

    @Override
    public void execute(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();
        int count = callCounts.merge(processInstanceId, 1, Integer::sum);

        Boolean shouldFailTimes = (Boolean) execution.getVariable("failTwice");
        if (Boolean.TRUE.equals(shouldFailTimes) && count <= 2) {
            throw new RuntimeException("Transient error on attempt " + count);
        }
        execution.setVariable("dataProcessed", true);
        callCounts.remove(processInstanceId);
    }
}
