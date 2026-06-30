package org.operaton.examples.bankaccountopening.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class NotifyRejectionDelegate implements JavaDelegate {

    private final NotificationService notificationService;

    public NotifyRejectionDelegate(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String email = (String) execution.getVariable("email");
        String fullName = (String) execution.getVariable("fullName");
        // reason: use rationale if available, else generic message
        String rationale = (String) execution.getVariable("rationale");
        String reason = (rationale != null && !rationale.isBlank())
            ? rationale
            : "Your application did not meet our current requirements.";
        execution.setVariable("status", "REJECTED");
        notificationService.sendRejection(email, fullName, reason);
    }
}
