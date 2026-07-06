package org.operaton.examples.approvalslametrics.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "demo.load-generator.enabled=false")
@Testcontainers
class DemoBeansWiringIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    ApplicationContext context;

    @Test
    void demoBeansAbsentWhenFlagDisabled() {
        assertThat(context.getBeansOfType(RequisitionLoadGenerator.class)).isEmpty();
        assertThat(context.getBeansOfType(SimulatedReviewer.class)).isEmpty();
    }
}
