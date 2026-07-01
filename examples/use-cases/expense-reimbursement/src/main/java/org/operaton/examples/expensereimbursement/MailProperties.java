package org.operaton.examples.expensereimbursement;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("mail")
public class MailProperties {
    private String from = "reimbursement@example.com";
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
}
