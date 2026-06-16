package org.operaton.examples.asynccontinuation.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("fetchDataDelegate")
public class FetchDataDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("dataFetched", true);
        execution.setVariable("recordCount", 42);
    }
}
