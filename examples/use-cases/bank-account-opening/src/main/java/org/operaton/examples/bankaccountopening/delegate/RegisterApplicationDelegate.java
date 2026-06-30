package org.operaton.examples.bankaccountopening.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RegisterApplicationDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("applicationId", UUID.randomUUID().toString());
        execution.setVariable("status", "RECEIVED");
        execution.setVariable("submittedAt", Instant.now().toString());
    }
}
