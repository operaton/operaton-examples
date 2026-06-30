package org.operaton.examples.bankaccountopening.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class NotifyApprovalDelegate implements JavaDelegate {

    private final NotificationService notificationService;

    public NotifyApprovalDelegate(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String email = (String) execution.getVariable("email");
        String fullName = (String) execution.getVariable("fullName");
        String iban = (String) execution.getVariable("iban");
        String accountType = (String) execution.getVariable("requestedAccountType");
        notificationService.sendApproval(email, fullName, iban, accountType);
    }
}
