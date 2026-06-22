package org.operaton.examples.employeeonboarding;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class EmployeeOnboardingIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void processDefinitionsAreDeployed() {
        var repoSvc = processEngine.getRepositoryService();
        assertThat(repoSvc.createProcessDefinitionQuery()
            .processDefinitionKey("employee-onboarding").count()).isEqualTo(1);
        assertThat(repoSvc.createProcessDefinitionQuery()
            .processDefinitionKey("provision-equipment").count()).isEqualTo(1);
        assertThat(repoSvc.createProcessDefinitionQuery()
            .processDefinitionKey("grant-system-access").count()).isEqualTo(1);
    }
}
