package org.operaton.examples.bankaccountopening.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OpenAccountDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        // Generate a plausible DE IBAN (not bank-valid — example only)
        String bankCode = "10020030";
        long accountNumber = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        String bban = bankCode + accountNumber;
        // Simplified: skip check-digit calculation for demo purposes
        String iban = "DE00" + bban;
        execution.setVariable("iban", iban);
        execution.setVariable("status", "OPENED");
        execution.setVariable("openedAt", Instant.now().toString());
    }
}
