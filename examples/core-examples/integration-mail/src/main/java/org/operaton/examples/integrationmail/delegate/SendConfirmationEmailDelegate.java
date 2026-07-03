package org.operaton.examples.integrationmail.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component("sendConfirmationEmailDelegate")
public class SendConfirmationEmailDelegate implements JavaDelegate {

    private final JavaMailSender mailSender;

    public SendConfirmationEmailDelegate(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String applicantEmail = (String) execution.getVariable("applicantEmail");
        String applicantName = (String) execution.getVariable("applicantName");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(applicantEmail);
        message.setFrom("hr@company.com");
        message.setSubject("Application Received — " + applicantName);
        message.setText("Dear " + applicantName + ",\n\nThank you for your application. We will review it shortly.\n\nBest regards,\nHR Team");
        mailSender.send(message);
    }
}
