package org.operaton.examples.inclusivegateway;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
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
class InsuranceClaimProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    @Test
    void medicalClaimRoutesToMedicalTrackOnly() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "insurance-claim", Map.of("claimType", "medical"));

        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(instance.getId()).list();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskDefinitionKey()).isEqualTo("Task_ReviewMedical");

        taskService.complete(tasks.get(0).getId());
        assertCompleted(instance, "EndEvent_ClaimSettled");
    }

    @Test
    void propertyClaimRoutesToPropertyTrackOnly() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "insurance-claim", Map.of("claimType", "property"));

        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(instance.getId()).list();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskDefinitionKey()).isEqualTo("Task_ReviewProperty");

        taskService.complete(tasks.get(0).getId());
        assertCompleted(instance, "EndEvent_ClaimSettled");
    }

    @Test
    void bothClaimTypeActivatesBothTracksAndJoinsBeforeSettlement() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "insurance-claim", Map.of("claimType", "both"));

        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(instance.getId()).list();
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(Task::getTaskDefinitionKey)
            .containsExactlyInAnyOrder("Task_ReviewMedical", "Task_ReviewProperty");

        // Complete medical first — process should NOT yet be settled (waiting for property)
        Task medicalTask = tasks.stream()
            .filter(t -> "Task_ReviewMedical".equals(t.getTaskDefinitionKey())).findFirst().get();
        taskService.complete(medicalTask.getId());

        // Process still active — property task still pending
        assertThat(runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId()).count()).isEqualTo(1);

        // Complete property task — now both done, join fires, claim settles
        Task propertyTask = taskService.createTaskQuery()
            .processInstanceId(instance.getId()).singleResult();
        taskService.complete(propertyTask.getId());

        assertCompleted(instance, "EndEvent_ClaimSettled");
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance h = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(h.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(h.getEndActivityId()).isEqualTo(endEventId);
    }
}
