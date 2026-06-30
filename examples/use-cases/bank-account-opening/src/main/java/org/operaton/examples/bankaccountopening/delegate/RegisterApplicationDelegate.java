package org.operaton.examples.bankaccountopening.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RegisterApplicationDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // applicationId is already seeded by AccountController as the business key
        execution.setVariable("status", "RECEIVED");
        execution.setVariable("submittedAt", Instant.now().toString());
    }
}
