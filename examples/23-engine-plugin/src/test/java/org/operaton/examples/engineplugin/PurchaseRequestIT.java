package org.operaton.examples.engineplugin;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
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

@SpringBootTest
@Testcontainers
class PurchaseRequestIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;
    @Autowired AuditLog auditLog;

    @Test
    void budgetAvailable_managerApprovesRequest() {
        // amount 1000 < 5000 → budgetAvailable = true → manager approval task
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "purchase-request", Map.of("amount", 1000.0));

        Task task = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("managers")
                .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("UserTask_ManagerApproval");

        taskService.complete(task.getId());

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Approved");

        // Audit log must have recorded the completed user task
        assertThat(auditLog.getEntries()).hasSize(1);
        AuditLog.AuditEntry entry = auditLog.getEntries().get(0);
        assertThat(entry.taskId()).isEqualTo(task.getId());
        assertThat(entry.taskName()).isEqualTo("Manager approval");
    }

    @Test
    void budgetNotAvailable_requestRejectedWithoutUserTask() {
        // amount 9000 >= 5000 → budgetAvailable = false → rejected end event
        int entriesBefore = auditLog.getEntries().size();

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "purchase-request", Map.of("amount", 9000.0));

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Rejected");

        // No user task was reached, so audit log must not have grown
        assertThat(auditLog.getEntries()).hasSize(entriesBefore);
    }

    private HistoricProcessInstance historicInstance(ProcessInstance instance) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
    }
}
