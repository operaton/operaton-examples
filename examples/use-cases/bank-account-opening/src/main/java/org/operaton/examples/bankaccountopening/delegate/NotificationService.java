package org.operaton.examples.bankaccountopening.delegate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendApproval(String to, String fullName, String iban, String accountType) {
        send(to,
            "Your bank account has been opened",
            "Dear " + fullName + ",\n\n"
            + "Congratulations! Your " + accountType + " account has been successfully opened.\n"
            + "IBAN: " + iban + "\n\n"
            + "Welcome to our bank.\n\nBank Example");
    }

    public void sendRejection(String to, String fullName, String reason) {
        send(to,
            "Your bank account application",
            "Dear " + fullName + ",\n\n"
            + "Thank you for your application. Unfortunately, we are unable to open an account at this time.\n"
            + "Reason: " + reason + "\n\n"
            + "If you have questions, please contact our support team.\n\nBank Example");
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        mailSender.send(msg);
    }
}
