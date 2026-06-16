package org.operaton.examples.engineplugin;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("checkBudgetDelegate")
public class CheckBudgetDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // Budget is available if the requested amount is below 5000
        Double amount = (Double) execution.getVariable("amount");
        execution.setVariable("budgetAvailable", amount == null || amount < 5000.0);
    }
}
