package org.operaton.examples.commandinterceptor;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("reviewTaskDelegate")
public class ReviewTaskDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // Check inventory - in real scenario would call external service
        execution.setVariable("inventoryAvailable", true);
    }
}
