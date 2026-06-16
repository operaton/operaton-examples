package org.operaton.examples.timerevents;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "operaton.bpm.job-execution.enabled=false")
@Testcontainers
class SlaEscalationProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired ManagementService managementService;
    @Autowired HistoryService historyService;

    @Test
    void taskCompletedBeforeTimerFiresFollowsNormalPath() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "sla-escalation", Map.of("requestId", "REQ-001"));

        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("UserTask_HandleRequest");

        taskService.complete(task.getId(), Map.of("resolution", "resolved"));

        assertCompleted(instance, "EndEvent_Resolved");
    }

    @Test
    void timerBoundaryEventEscalatesOverdueTask() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "sla-escalation", Map.of("requestId", "REQ-002"));

        Job timerJob = managementService.createJobQuery()
            .processInstanceId(instance.getId())
            .timers()
            .singleResult();
        assertThat(timerJob).isNotNull();

        managementService.executeJob(timerJob.getId());

        assertCompleted(instance, "EndEvent_Escalated");
    }

    @Test
    void timerJobIsRemovedWhenTaskIsCompletedBeforeEscalation() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "sla-escalation", Map.of("requestId", "REQ-003"));

        assertThat(managementService.createJobQuery()
            .processInstanceId(instance.getId()).timers().count()).isEqualTo(1);

        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId()).singleResult();
        taskService.complete(task.getId());

        assertThat(managementService.createJobQuery()
            .processInstanceId(instance.getId()).timers().count()).isEqualTo(0);
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo(endEventId);
    }
}
