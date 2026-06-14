package org.operaton.examples.servicetasks;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class EnrichPaymentDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Object amountVar = execution.getVariable("amount");
        if (amountVar == null) {
            throw new BpmnError("INVALID_AMOUNT", "Payment amount is required");
        }
        double amount = (Double) amountVar;
        if (amount <= 0) {
            throw new BpmnError("INVALID_AMOUNT", "Payment amount must be positive");
        }
        // Runs in the engine thread / caller transaction — same as the startProcessInstance call.
        // Any exception here rolls back to the last persistent state (before the service task).
        execution.setVariable("currency", "USD");
        execution.setVariable("enriched", true);
    }
}
