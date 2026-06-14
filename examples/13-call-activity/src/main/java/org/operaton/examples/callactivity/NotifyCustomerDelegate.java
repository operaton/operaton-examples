package org.operaton.examples.callactivity;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Simulates sending a confirmation notification to the customer.
 * Sets {@code notified = true} to signal that the notification was dispatched.
 */
@Component
public class NotifyCustomerDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("notified", true);
    }
}
