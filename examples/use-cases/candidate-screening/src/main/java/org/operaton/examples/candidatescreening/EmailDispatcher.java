package org.operaton.examples.candidatescreening;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailDispatcher {

    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;

    public EmailDispatcher(JavaMailSender javaMailSender, MailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.mailProperties = mailProperties;
    }

    public void sendInvitation(String to, String candidateName, String position,
                               String body, String interviewSlot) {
        // body is the LLM-drafted invitation text, which already embeds interviewSlot
        send(to, "Interview Invitation: " + position, body);
    }

    public void sendRecruiterSummary(String to, String candidateName, String position,
                                     String body) {
        send(to, "Candidate Summary: " + candidateName + " — " + position, body);
    }

    public void sendRejection(String to, String candidateName, String position,
                              String body) {
        send(to, "Application Update: " + position, body);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailProperties.getFrom());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        javaMailSender.send(msg);
    }
}
