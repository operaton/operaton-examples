package org.operaton.examples.asynccontinuation.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component("processDataDelegate")
public class ProcessDataDelegate implements JavaDelegate {

    private static final ConcurrentHashMap<String, Integer> callCounts
        = new ConcurrentHashMap<>();

    @Override
    public void execute(DelegateExecution execution) {
        String pid = execution.getProcessInstanceId();
        int attempt = callCounts.merge(pid, 1, Integer::sum);
        Boolean failTwice = (Boolean) execution.getVariable("failTwice");
        if (Boolean.TRUE.equals(failTwice) && attempt <= 2) {
            throw new RuntimeException("Transient failure on attempt " + attempt);
        }
        execution.setVariable("dataProcessed", true);
        callCounts.remove(pid);
    }
}
