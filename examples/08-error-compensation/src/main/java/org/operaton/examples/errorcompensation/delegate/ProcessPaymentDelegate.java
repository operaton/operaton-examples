package org.operaton.examples.errorcompensation.delegate;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("processPaymentDelegate")
public class ProcessPaymentDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        Boolean shouldFail = (Boolean) execution.getVariable("paymentShouldFail");
        if (Boolean.TRUE.equals(shouldFail)) {
            throw new BpmnError("PAYMENT_FAILED", "Payment declined");
        }
        execution.setVariable("paymentConfirmed", true);
    }
}
