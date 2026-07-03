package org.operaton.examples.processmigration;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("fraudCheckDelegate")
public class FraudCheckDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("fraudRisk", "low"); // simulated
    }
}
