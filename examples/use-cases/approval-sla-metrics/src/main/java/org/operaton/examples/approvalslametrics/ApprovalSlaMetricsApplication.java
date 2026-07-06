package org.operaton.examples.approvalslametrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApprovalSlaMetricsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalSlaMetricsApplication.class, args);
    }
}
