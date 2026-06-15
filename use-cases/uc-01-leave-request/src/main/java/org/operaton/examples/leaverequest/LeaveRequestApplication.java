package org.operaton.examples.leaverequest;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Clock;

@SpringBootApplication
public class LeaveRequestApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveRequestApplication.class, args);
    }

    @Bean
    Clock leaveRequestClock() {
        return Clock.systemDefaultZone();
    }
}
