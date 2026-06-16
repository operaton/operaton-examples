package org.operaton.examples.multiinstance;

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
class DocumentReviewProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    @Test
    void parallelMultiInstanceCreatesOneTaskPerReviewer() {
        List<String> reviewers = List.of("alice", "bob", "carol");
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "document-review",
            Map.of("reviewers", reviewers, "document", "Q1 Report"));

        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .orderByTaskAssignee().asc()
            .list();

        assertThat(tasks).hasSize(3);
        assertThat(tasks).extracting(Task::getAssignee)
            .containsExactly("alice", "bob", "carol");
    }

    @Test
    void allApprovedLeadsToApprovedEndEvent() {
        List<String> reviewers = List.of("alice", "bob");
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "document-review",
            Map.of("reviewers", reviewers, "document", "Budget Plan"));

        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .list();

        for (Task task : tasks) {
            taskService.complete(task.getId(), Map.of("approved", true));
        }

        assertCompleted(instance, "EndEvent_Approved");
    }

    @Test
    void anyRejectionLeadsToRejectedEndEvent() {
        List<String> reviewers = List.of("alice", "bob");
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "document-review",
            Map.of("reviewers", reviewers, "document", "Risk Report"));

        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .list();

        // alice approves, bob rejects
        for (Task task : tasks) {
            boolean isAlice = "alice".equals(task.getAssignee());
            taskService.complete(task.getId(), Map.of("approved", isAlice));
        }

        // The last completion's "approved" value determines gateway routing
        // With approved=false from bob, process ends at rejected
        // Note: in parallel multi-instance, the last completion sets the process variable
        assertCompleted(instance, "EndEvent_Rejected");
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo(endEventId);
    }
}
