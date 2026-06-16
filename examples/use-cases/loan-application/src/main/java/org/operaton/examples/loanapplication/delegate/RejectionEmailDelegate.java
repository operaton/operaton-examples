package org.operaton.examples.loanapplication.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component("rejectionEmailDelegate")
public class RejectionEmailDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(RejectionEmailDelegate.class);

    private final JavaMailSender mailSender;

    public RejectionEmailDelegate(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String applicantEmail = (String) execution.getVariable("applicantEmail");
        execution.setVariable("loanDecision", "REJECTED");
        log.info("Loan application REJECTED. Risk level: {}", execution.getVariable("riskLevel"));

        if (applicantEmail == null || applicantEmail.isBlank()) {
            return; // no email to send
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(applicantEmail);
        message.setSubject("Loan Application Rejected");
        message.setText("Your loan application has been rejected. Risk level: " + execution.getVariable("riskLevel"));
        mailSender.send(message);
        log.info("Rejection email sent to {}", applicantEmail);
    }
}
