package org.operaton.examples.candidatescreening;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

    @Mock JavaMailSender javaMailSender;
    @Captor ArgumentCaptor<SimpleMailMessage> messageCaptor;

    MailProperties mailProperties;
    EmailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mailProperties = new MailProperties();
        mailProperties.setFrom("screening@example.com");
        dispatcher = new EmailDispatcher(javaMailSender, mailProperties);
    }

    @Test
    void sendInvitation_usesCorrectRecipientAndSubject() {
        dispatcher.sendInvitation("candidate@example.com", "Ada Lindqvist",
            "Java Engineer", "Dear Ada...", "2026-06-22T10:00:00");

        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage msg = messageCaptor.getValue();
        assertThat(msg.getTo()).containsExactly("candidate@example.com");
        assertThat(msg.getSubject()).isEqualTo("Interview Invitation: Java Engineer");
        assertThat(msg.getFrom()).isEqualTo("screening@example.com");
        assertThat(msg.getText()).isEqualTo("Dear Ada...");
    }

    @Test
    void sendRejection_usesCorrectRecipientAndSubject() {
        dispatcher.sendRejection("candidate@example.com", "Wes Park",
            "Java Engineer", "Dear Wes, we regret...");

        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage msg = messageCaptor.getValue();
        assertThat(msg.getTo()).containsExactly("candidate@example.com");
        assertThat(msg.getSubject()).isEqualTo("Application Update: Java Engineer");
        assertThat(msg.getFrom()).isEqualTo("screening@example.com");
        assertThat(msg.getText()).isEqualTo("Dear Wes, we regret...");
    }

    @Test
    void sendRecruiterSummary_usesCorrectRecipientAndSubject() {
        dispatcher.sendRecruiterSummary("rachel@example.com", "Ada Lindqvist",
            "Java Engineer", "Score: 88...");

        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage msg = messageCaptor.getValue();
        assertThat(msg.getTo()).containsExactly("rachel@example.com");
        assertThat(msg.getSubject()).isEqualTo("Candidate Summary: Ada Lindqvist — Java Engineer");
        assertThat(msg.getFrom()).isEqualTo("screening@example.com");
        assertThat(msg.getText()).isEqualTo("Score: 88...");
    }
}
