package org.operaton.examples.eventsubprocess;

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
class DocumentProcessingIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    @Test
    void normalPathParsesEnrichesAndWaitsAtStore() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "document-processing", Map.of("simulateError", false));

        // After service tasks, process should be at user task
        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskDefinitionKey("Task_StoreDocument")
            .singleResult();
        assertThat(task).isNotNull();

        taskService.complete(task.getId());
        assertCompleted(instance, "EndEvent_Stored");
    }

    @Test
    void auditSignalTriggersNonInterruptingSubprocess() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "document-processing", Map.of("simulateError", false));

        // Process is at user task — broadcast audit signal
        runtimeService.signalEventReceived("AuditRequired");

        // Audit event subprocess runs; main flow still active at user task
        // Verify audit was recorded
        var auditVar = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName("auditRecorded")
            .singleResult();
        assertThat(auditVar).isNotNull();
        assertThat(auditVar.getValue()).isEqualTo(true);

        // Main flow still waits at user task (non-interrupting!)
        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(task).isNotNull();
        taskService.complete(task.getId());
    }

    @Test
    void documentErrorTriggersInterruptingErrorSubprocess() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "document-processing", Map.of("simulateError", true));

        // Interrupting error subprocess took over — main flow cancelled.
        // The engine sets endActivityId to the event subprocess element, not the
        // end event inside it, because the subprocess itself is the activity that
        // terminates the process scope.
        assertCompleted(instance, "EventSubProcess_Error");

        var errorHandled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName("errorHandled").singleResult();
        assertThat(errorHandled.getValue()).isEqualTo(true);
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance h = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(h.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(h.getEndActivityId()).isEqualTo(endEventId);
    }
}
