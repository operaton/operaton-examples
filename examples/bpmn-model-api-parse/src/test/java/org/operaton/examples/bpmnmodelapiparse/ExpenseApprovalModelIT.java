package org.operaton.examples.bpmnmodelapiparse;

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
class ExpenseApprovalModelIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;
    @Autowired ProcessModelInspector modelInspector;

    @Test
    void modelContainsExpectedElements() {
        ProcessModelReport report = modelInspector.inspectLatestDeployment("expense-approval");

        assertThat(report.processId()).isEqualTo("expense-approval");
        assertThat(report.userTaskNames()).contains("Manager review");
        assertThat(report.serviceTaskNames()).contains("Validate expense");
        assertThat(report.gatewayCount()).isEqualTo(1);
        assertThat(report.endEventNames()).contains("Expense approved", "Expense rejected");
    }

    @Test
    void smallExpenseReachesManagerReview() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "expense-approval", Map.of("amount", 1000.0));

        Task task = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getName()).isEqualTo("Manager review");

        taskService.complete(task.getId());

        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).contains("Approved");
    }

    @Test
    void largeExpenseIsRejectedAutomatically() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "expense-approval", Map.of("amount", 10000.0));

        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).contains("Rejected");
    }
}
