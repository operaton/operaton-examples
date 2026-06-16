package org.operaton.examples.jobretryprofile;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component("paymentServiceDelegate")
public class PaymentServiceDelegate implements JavaDelegate {

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile int failUntilAttempt = 0;

    public void configureToFailForAttempts(int n) {
        failUntilAttempt = n;
        failureCount.set(0);
    }

    @Override
    public void execute(DelegateExecution execution) {
        int attempt = failureCount.incrementAndGet();
        if (attempt <= failUntilAttempt) {
            throw new RuntimeException("Payment service temporarily unavailable (attempt " + attempt + ")");
        }
        execution.setVariable("paymentConfirmationCode", "PAY-" + System.currentTimeMillis());
    }
}
