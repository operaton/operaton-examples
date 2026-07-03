package org.operaton.examples.callactivity;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Validates the order ID format.
 * Sets {@code isValid = true} when the orderId starts with "ORD-", otherwise {@code false}.
 */
@Component
public class ValidateOrderDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String orderId = (String) execution.getVariable("orderId");
        boolean isValid = orderId != null && orderId.startsWith("ORD-");
        execution.setVariable("isValid", isValid);
    }
}
