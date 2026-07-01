package org.operaton.examples.expensereimbursement.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("paymentService")
public class PaymentService implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Override
    public void execute(DelegateExecution execution) {
        String requesterName = (String) execution.getVariable("requesterName");
        double statedCost = ((Number) execution.getVariable("statedCost")).doubleValue();
        String reference = "PAY-" + execution.getProcessInstanceId().substring(0, 8).toUpperCase();
        log.info("Simulating payment of {} EUR for {} — reference {}", statedCost, requesterName, reference);
        execution.setVariable("paymentReference", reference);
        execution.setVariable("paymentDate", LocalDate.now().toString());
    }
}
