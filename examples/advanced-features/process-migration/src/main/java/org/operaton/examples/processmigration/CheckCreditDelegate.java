package org.operaton.examples.processmigration;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("checkCreditDelegate")
public class CheckCreditDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        int creditScore = 750; // simulated
        execution.setVariable("creditScore", creditScore);
        execution.setVariable("creditApproved", creditScore >= 700);
    }
}
