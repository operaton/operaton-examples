package org.operaton.examples.callactivity;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderOrchestrationProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired RepositoryService repositoryService;

    @Test
    void validOrderCallsBothSubprocessesAndCompletes() {
        ProcessInstance parent = runtimeService.startProcessInstanceByKey(
            "order-orchestration",
            Map.of("orderId", "ORD-001", "customerEmail", "test@example.com"));

        assertCompleted(parent, "EndEvent_Confirmed");

        // Verify two child processes were called
        List<HistoricProcessInstance> children = historyService
            .createHistoricProcessInstanceQuery()
            .superProcessInstanceId(parent.getId())
            .list();
        assertThat(children).hasSize(2);
        assertThat(children).extracting(HistoricProcessInstance::getProcessDefinitionKey)
            .containsExactlyInAnyOrder("validate-order", "notify-customer");
    }

    @Test
    void invalidOrderSkipsNotificationAndEndsAtInvalidOrder() {
        ProcessInstance parent = runtimeService.startProcessInstanceByKey(
            "order-orchestration",
            Map.of("orderId", "INVALID-001", "customerEmail", "test@example.com"));

        assertCompleted(parent, "EndEvent_InvalidOrder");

        // Only validate-order child was called, not notify-customer
        List<HistoricProcessInstance> children = historyService
            .createHistoricProcessInstanceQuery()
            .superProcessInstanceId(parent.getId())
            .list();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getProcessDefinitionKey()).isEqualTo("validate-order");
    }

    @Test
    void allThreeProcessDefinitionsAreDeployed() {
        assertThat(repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("order-orchestration").count()).isEqualTo(1);
        assertThat(repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("validate-order").count()).isEqualTo(1);
        assertThat(repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("notify-customer").count()).isEqualTo(1);
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo(endEventId);
    }
}
