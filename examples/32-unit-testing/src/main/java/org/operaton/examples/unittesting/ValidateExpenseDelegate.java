package org.operaton.examples.unittesting;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class ValidateExpenseDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Double amount = (Double) execution.getVariable("amount");
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Expense amount must be positive");
        }
        execution.setVariable("valid", true);
    }
}
