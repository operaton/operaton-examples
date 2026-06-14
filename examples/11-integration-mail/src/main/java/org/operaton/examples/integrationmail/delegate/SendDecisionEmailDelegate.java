package org.operaton.examples.integrationmail.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component("sendDecisionEmailDelegate")
public class SendDecisionEmailDelegate implements JavaDelegate {

    private final JavaMailSender mailSender;

    public SendDecisionEmailDelegate(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String applicantEmail = (String) execution.getVariable("applicantEmail");
        String applicantName = (String) execution.getVariable("applicantName");
        Boolean approved = (Boolean) execution.getVariable("approved");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(applicantEmail);
        message.setFrom("hr@company.com");

        if (Boolean.TRUE.equals(approved)) {
            message.setSubject("Application Approved — " + applicantName);
            message.setText("Dear " + applicantName + ",\n\nCongratulations! Your application has been approved.\n\nBest regards,\nHR Team");
        } else {
            message.setSubject("Application Update — " + applicantName);
            message.setText("Dear " + applicantName + ",\n\nThank you for applying. After careful consideration, we will not be moving forward at this time.\n\nBest regards,\nHR Team");
        }
        mailSender.send(message);
    }
}
