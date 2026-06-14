package org.operaton.examples.servicetasks;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class ChargePaymentDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        boolean simulateDecline = Boolean.TRUE.equals(execution.getVariable("simulateDecline"));
        boolean simulateTransientError = Boolean.TRUE.equals(execution.getVariable("simulateTransientError"));

        if (simulateTransientError) {
            // RuntimeException → engine decrements retries and schedules a retry job.
            // The process instance stays at this async task.
            throw new RuntimeException("Payment gateway temporarily unavailable");
        }
        if (simulateDecline) {
            // BpmnError → engine routes to the matching boundary error event.
            throw new BpmnError("PAYMENT_DECLINED", "Card declined by payment provider");
        }

        // Happy path: record the charge reference.
        execution.setVariable("chargeId", "CHG-" + execution.getProcessInstanceId());
        execution.setVariable("charged", true);
    }
}
