package org.operaton.examples.incidentmanagement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IncidentManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentManagementApplication.class, args);
    }

    @Bean("timerDuration")
    String timerDuration(@Value("${timer.escalation.duration:PT1H}") String timerDuration) {
        return timerDuration;
    }
}
