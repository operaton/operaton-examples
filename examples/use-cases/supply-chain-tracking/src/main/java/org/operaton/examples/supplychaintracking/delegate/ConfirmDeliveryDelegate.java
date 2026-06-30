package org.operaton.examples.supplychaintracking.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component("confirmDeliveryDelegate")
public class ConfirmDeliveryDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("deliveredAt", Instant.now().toString());
        execution.setVariable("status", "DELIVERED");
    }
}
