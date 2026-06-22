package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("appraiseDamageDelegate")
public class AppraiseDamageDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(AppraiseDamageDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Double estimatedAmount = (Double) execution.getVariable("estimatedAmount");
        double appraisedAmount = estimatedAmount != null ? estimatedAmount * 0.9 : 0.0;
        execution.setVariable("appraisedAmount", appraisedAmount);
        log.info("Damage appraisal for claim {}: estimatedAmount={}, appraisedAmount={}",
            execution.getBusinessKey(), estimatedAmount, appraisedAmount);
    }
}
