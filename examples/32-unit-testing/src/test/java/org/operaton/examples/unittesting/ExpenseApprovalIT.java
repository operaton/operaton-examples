package org.operaton.examples.unittesting;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
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

/**
 * Integration test — full Spring Boot context with Testcontainers PostgreSQL.
 *
 * Verifies that the same process behaviour demonstrated by the unit tests also
 * holds against the production database stack.
 */
@SpringBootTest
@Testcontainers
class ExpenseApprovalIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    @Test
    void smallExpenseIsAutoApproved() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "expense-approval",
            Map.of("amount", 100.0)
        );

        assertThat(instance).isNotNull();
        // Process ends synchronously; no active instance remains
        var active = runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(active).isNull();

        var historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_AutoApproved");
    }

    @Test
    void largeExpenseNeedsManagerApproval() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "expense-approval",
            Map.of("amount", 1500.0)
        );

        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskCandidateGroup("managers")
            .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getName()).isEqualTo("Manager approval");

        taskService.complete(task.getId());

        // Process should now be completed
        var active = runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(active).isNull();

        var historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Approved");
    }
}
