package org.operaton.examples.loanapplication.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(NotificationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Object riskLevel = execution.getVariable("riskLevel");
        Object creditScore = execution.getVariable("creditScore");

        execution.setVariable("loanDecision", "APPROVED");
        log.info("Loan application APPROVED. Risk level: {}, Credit score: {}", riskLevel, creditScore);
    }
}
