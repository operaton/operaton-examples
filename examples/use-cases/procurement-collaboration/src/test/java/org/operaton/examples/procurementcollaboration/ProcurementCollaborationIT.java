package org.operaton.examples.procurementcollaboration;

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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProcurementCollaborationIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void processDefinitionsAreDeployed() {
        var repo = processEngine.getRepositoryService();
        assertThat(repo.createProcessDefinitionQuery()
            .processDefinitionKey("purchase-request").count()).isEqualTo(1);
        assertThat(repo.createProcessDefinitionQuery()
            .processDefinitionKey("quote-handling").count()).isEqualTo(1);
    }

    // Helper: find the completed quote-handling instance with the given requestId.
    // The two processes are not parent/child (no call activity), so there is no
    // superProcessInstanceId link; we locate by variable value.
    private HistoricProcessInstance findSupplierInstance(String requestId) {
        var supplierInstances = historyService.createHistoricProcessInstanceQuery()
            .processDefinitionKey("quote-handling")
            .list();
        return supplierInstances.stream()
            .filter(hi -> {
                var v = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(hi.getId())
                    .variableName("requestId")
                    .singleResult();
                return v != null && requestId.equals(v.getValue());
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No quote-handling instance found for requestId=" + requestId));
    }
}
