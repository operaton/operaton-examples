package org.operaton.examples.gettingstarted;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderApprovalProcessIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    @Test
    void smallOrderIsApprovedAutomatically() {
        ProcessInstance instance = startOrder(2, 100.0); // total 200 < 1000

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_OrderApproved");
        assertThat(historicVariable(instance, "orderTotal")).isEqualTo(200.0);
    }

    @Test
    void largeOrderRequiresApprovalAndCanBeApproved() {
        ProcessInstance instance = startOrder(3, 500.0); // total 1500 >= 1000

        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskCandidateGroup("approvers")
            .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("UserTask_ApproveOrder");

        taskService.complete(task.getId(), Map.of("approved", true));

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_OrderApproved");
    }

    @Test
    void largeOrderCanBeRejected() {
        ProcessInstance instance = startOrder(10, 500.0); // total 5000 >= 1000

        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        taskService.complete(task.getId(), Map.of("approved", false));

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_OrderRejected");
    }

    private ProcessInstance startOrder(int quantity, double unitPrice) {
        return runtimeService.startProcessInstanceByKey("order-approval",
            Map.of("quantity", quantity, "unitPrice", unitPrice));
    }

    private HistoricProcessInstance historicInstance(ProcessInstance instance) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
    }

    private Object historicVariable(ProcessInstance instance, String name) {
        return historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName(name)
            .singleResult()
            .getValue();
    }
}
