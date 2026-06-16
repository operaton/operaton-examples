package org.operaton.examples.commandinterceptor;

import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SupplyChainApprovalIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;
    @Autowired CommandAuditLog auditLog;

    @BeforeEach
    void clearLog() {
        auditLog.clear();
    }

    @Test
    void commandsAreAuditedDuringProcessExecution() {
        // Start process — this issues a StartProcessInstanceByKey command
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("supply-chain-approval");

        // At least one command should have been recorded
        assertThat(auditLog.getEntries()).isNotEmpty();

        // The start command should appear in the log
        boolean hasStartCommand = auditLog.getEntries().stream()
                .anyMatch(e -> e.commandName().contains("StartProcess") || e.commandName().contains("StartProcessInstance"));
        assertThat(hasStartCommand).isTrue();

        // All recorded durations should be non-negative
        auditLog.getEntries().forEach(e -> assertThat(e.durationMs()).isGreaterThanOrEqualTo(0));

        // Complete the user task
        Task task = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(task).isNotNull();

        int entriesBefore = auditLog.getEntries().size();
        taskService.complete(task.getId());

        // Completing a task should add more audit entries
        assertThat(auditLog.getEntries().size()).isGreaterThan(entriesBefore);

        // Process should have completed
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
    }

    @Test
    void allAuditEntriesHaveCommandName() {
        runtimeService.startProcessInstanceByKey("supply-chain-approval");

        assertThat(auditLog.getEntries()).allMatch(e -> e.commandName() != null && !e.commandName().isEmpty());
    }
}
