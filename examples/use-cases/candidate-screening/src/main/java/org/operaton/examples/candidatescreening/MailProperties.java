package org.operaton.examples.candidatescreening;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("mail")
public class MailProperties {
    private String from = "screening@example.com";

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
}
