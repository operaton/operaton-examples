package org.operaton.examples.gettingstarted;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class CalculateOrderTotalDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        int quantity = (Integer) execution.getVariable("quantity");
        double unitPrice = (Double) execution.getVariable("unitPrice");
        execution.setVariable("orderTotal", quantity * unitPrice);
    }
}
