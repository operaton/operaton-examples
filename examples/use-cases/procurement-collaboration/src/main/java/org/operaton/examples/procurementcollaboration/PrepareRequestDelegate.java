package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("prepareRequestDelegate")
public class PrepareRequestDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(PrepareRequestDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        if (execution.getVariable("requestId") == null) execution.setVariable("requestId", "REQ-1");
        if (execution.getVariable("item") == null) execution.setVariable("item", "widget");
        if (execution.getVariable("quantity") == null) execution.setVariable("quantity", 1);
        if (execution.getVariable("maxBudget") == null) execution.setVariable("maxBudget", 5000);
        log.info("Prepared request: requestId={}, item={}, quantity={}, maxBudget={}",
            execution.getVariable("requestId"), execution.getVariable("item"),
            execution.getVariable("quantity"), execution.getVariable("maxBudget"));
    }
}
