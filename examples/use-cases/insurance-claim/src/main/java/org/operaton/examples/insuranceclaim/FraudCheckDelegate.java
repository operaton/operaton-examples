package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("fraudCheckDelegate")
public class FraudCheckDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(FraudCheckDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Double estimatedAmount = (Double) execution.getVariable("estimatedAmount");
        boolean fraudSuspected = estimatedAmount != null && estimatedAmount > 100_000;
        execution.setVariable("fraudSuspected", fraudSuspected);
        log.info("Fraud check for claim {}: estimatedAmount={}, fraudSuspected={}",
            execution.getBusinessKey(), estimatedAmount, fraudSuspected);
    }
}
