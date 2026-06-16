package org.operaton.examples.leaverequest.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EscalationReminderDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(EscalationReminderDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String requester = (String) execution.getVariable("requester");
        log.warn("Escalation reminder: manager review for '{}' has not been completed. " +
                "Please review the pending leave request.", requester);
        execution.setVariable("escalated", true);
    }
}
