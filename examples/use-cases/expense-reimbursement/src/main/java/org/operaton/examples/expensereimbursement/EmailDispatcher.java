package org.operaton.examples.expensereimbursement;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component("emailDispatcher")
public class EmailDispatcher implements JavaDelegate {
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailDispatcher(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String to = (String) execution.getVariable("requesterEmail");
        String subject = (String) execution.getVariable("emailSubject");
        String body = (String) execution.getVariable("emailBody");

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailProperties.getFrom());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
