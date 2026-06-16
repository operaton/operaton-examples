package org.operaton.examples.leaverequest.delegate;

import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.identity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component("leaveRejectionEmailDelegate")
public class LeaveRejectionEmailDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(LeaveRejectionEmailDelegate.class);

    private final JavaMailSender mailSender;
    private final IdentityService identityService;

    public LeaveRejectionEmailDelegate(JavaMailSender mailSender, IdentityService identityService) {
        this.mailSender = mailSender;
        this.identityService = identityService;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String requesterUserId = (String) execution.getVariable("requester");
        String startDate = (String) execution.getVariable("startDate");
        String endDate = (String) execution.getVariable("endDate");

        log.info("Sending leave rejection email to requester '{}'", requesterUserId);

        if (requesterUserId == null || requesterUserId.isBlank()) {
            log.warn("No requester variable set — skipping rejection email");
            return;
        }

        User user = identityService.createUserQuery().userId(requesterUserId).singleResult();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("No email address found for user '{}' — skipping rejection email", requesterUserId);
            return;
        }

        String firstName = user.getFirstName() != null ? user.getFirstName() : requesterUserId;
        String start = startDate != null ? startDate : "N/A";
        String end = endDate != null ? endDate : "N/A";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Leave Request — Rejected");
        message.setText(
            "Dear " + firstName + ",\n\n"
            + "Your leave request for " + start + " to " + end + " has been rejected.\n\n"
            + "Please contact your manager for more details.\n\n"
            + "Kind regards,\nHR Department"
        );
        try {
            mailSender.send(message);
            log.info("Rejection email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send rejection email to '{}': {}", user.getEmail(), e.getMessage());
        }
    }
}
