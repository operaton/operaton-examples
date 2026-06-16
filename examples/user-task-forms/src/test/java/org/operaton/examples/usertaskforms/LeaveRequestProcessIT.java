package org.operaton.examples.usertaskforms;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.FormService;
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
class LeaveRequestProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired FormService formService;
    @Autowired HistoryService historyService;

    @Test
    void approvedLeaveRequestCompletesSuccessfully() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("leave-request");

        // Employee submits the leave request form
        Task submitTask = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskCandidateGroup("employees")
            .singleResult();
        assertThat(submitTask).isNotNull();
        assertThat(submitTask.getTaskDefinitionKey()).isEqualTo("UserTask_SubmitRequest");

        taskService.claim(submitTask.getId(), "alice");
        formService.submitTaskForm(submitTask.getId(), Map.of(
            "employeeName", "Alice",
            "startDate", "2026-07-01",
            "endDate", "2026-07-05",
            "leaveType", "annual"
        ));

        // Manager reviews and approves
        Task reviewTask = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskCandidateGroup("managers")
            .singleResult();
        assertThat(reviewTask).isNotNull();
        assertThat(reviewTask.getTaskDefinitionKey()).isEqualTo("UserTask_ReviewRequest");

        taskService.claim(reviewTask.getId(), "bob");
        formService.submitTaskForm(reviewTask.getId(), Map.of(
            "approved", true,
            "comments", "Approved — enjoy your holiday!"
        ));

        // Process ends at LeaveApproved
        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_LeaveApproved");
        assertThat(historicVariable(instance, "leaveType")).isEqualTo("annual");
    }

    @Test
    void rejectedLeaveRequestEndsAtRejectedEvent() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("leave-request");

        Task submitTask = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskCandidateGroup("employees")
            .singleResult();
        taskService.claim(submitTask.getId(), "alice");
        formService.submitTaskForm(submitTask.getId(), Map.of(
            "employeeName", "Alice",
            "startDate", "2026-12-24",
            "endDate", "2026-12-26",
            "leaveType", "annual"
        ));

        Task reviewTask = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskCandidateGroup("managers")
            .singleResult();
        taskService.claim(reviewTask.getId(), "bob");
        formService.submitTaskForm(reviewTask.getId(), Map.of(
            "approved", false,
            "comments", "Insufficient notice for holiday period."
        ));

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_LeaveRejected");
    }

    @Test
    void formDataIsAccessibleViaFormService() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("leave-request");

        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();

        // Verify the form fields are defined on the task
        var formData = formService.getTaskFormData(task.getId());
        assertThat(formData.getFormFields())
            .extracting("id")
            .containsExactlyInAnyOrder("employeeName", "startDate", "endDate", "leaveType");
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
