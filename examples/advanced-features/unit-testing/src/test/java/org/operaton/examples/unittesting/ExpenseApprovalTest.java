package org.operaton.examples.unittesting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.test.Deployment;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test — no Spring Boot, no Docker, uses in-memory H2 for speed.
 *
 * ProcessEngineExtension creates a standalone in-memory ProcessEngine from the
 * provided configurator. The @Deployment annotation deploys the BPMN before
 * each test method and undeploys after. ValidateExpenseDelegate is instantiated
 * via reflection (operaton:class), so no Spring context is needed.
 */
class ExpenseApprovalTest {

    @RegisterExtension
    static ProcessEngineExtension engine = ProcessEngineExtension.builder()
        .configurator(cfg -> cfg
            .setJdbcUrl("jdbc:h2:mem:expense-unit-test;DB_CLOSE_DELAY=-1")
            .setJdbcDriver("org.h2.Driver")
            .setDatabaseSchemaUpdate("true"))
        .closeEngineAfterAllTests()
        .build();

    @Test
    @Deployment(resources = "expense-approval.bpmn")
    void smallExpenseIsAutoApproved() {
        RuntimeService runtimeService = engine.getRuntimeService();
        HistoryService historyService = engine.getHistoryService();

        var instance = runtimeService.startProcessInstanceByKey(
            "expense-approval",
            Map.of("amount", 100.0)
        );

        assertThat(instance).isNotNull();
        // Small amount: process ends immediately at EndEvent_AutoApproved
        var historicInstance = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historicInstance.getEndActivityId()).isEqualTo("EndEvent_AutoApproved");
    }

    @Test
    @Deployment(resources = "expense-approval.bpmn")
    void largeExpenseNeedsManagerApproval() {
        RuntimeService runtimeService = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();
        HistoryService historyService = engine.getHistoryService();

        runtimeService.startProcessInstanceByKey(
            "expense-approval",
            Map.of("amount", 1000.0)
        );

        // Large amount: process waits at manager approval task
        var task = taskService.createTaskQuery()
            .taskCandidateGroup("managers")
            .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getName()).isEqualTo("Manager approval");

        // Manager approves
        taskService.complete(task.getId());

        // Process should now be ended at EndEvent_Approved
        var historicInstance = historyService
            .createHistoricProcessInstanceQuery()
            .singleResult();
        assertThat(historicInstance.getEndActivityId()).isEqualTo("EndEvent_Approved");
    }
}
