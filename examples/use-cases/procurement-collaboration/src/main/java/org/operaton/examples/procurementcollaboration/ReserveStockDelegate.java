package org.operaton.examples.procurementcollaboration;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("reserveStockDelegate")
public class ReserveStockDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("reserved", true);
        log.info("Stock reserved for requestId={}", execution.getVariable("requestId"));
    }
}
