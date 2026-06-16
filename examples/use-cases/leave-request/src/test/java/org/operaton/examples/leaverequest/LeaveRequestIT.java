package org.operaton.examples.leaverequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.FormService;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.form.FormField;
import org.operaton.bpm.engine.history.HistoricVariableInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LeaveRequestIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private FormService formService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetVacationBalances() {
        jdbcTemplate.update(
                "UPDATE employee_vacation_balance SET remaining_vacation_days = ? WHERE employee_id = ?",
                30,
                "alice"
        );
        jdbcTemplate.update(
                "UPDATE employee_vacation_balance SET remaining_vacation_days = ? WHERE employee_id = ?",
                30,
                "bob"
        );
        jdbcTemplate.update(
                "UPDATE employee_vacation_balance SET remaining_vacation_days = ? WHERE employee_id = ?",
                30,
                "carol"
        );
    }

    @Test
    void processDefinitionIsDeployed() {
        long count = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("leave-request")
                .count();

        assertThat(count).as("leave-request process definition must be deployed").isEqualTo(1);
    }

    @Test
    void startForm_exposesVacationInputs() {
        String processDefinitionId = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("leave-request")
                .singleResult()
                .getId();

        assertThat(formService.getStartFormData(processDefinitionId).getFormFields())
                .extracting(FormField::getId)
                .contains("startDate", "durationDays");
    }

    @Test
    void approvalPath_persistsRequestedDataAndDeductsBalanceAfterHrFinalization() {
        LocalDate requestedStartDate = LocalDate.now().plusDays(2);
        ProcessInstance instance = startLeaveRequest("alice", requestedStartDate, 5);

        assertThat(runtimeService.getVariable(instance.getId(), "requester")).isEqualTo("alice");
        assertThat(runtimeService.getVariable(instance.getId(), "startDate")).isEqualTo(requestedStartDate.toString());
        assertThat(runtimeService.getVariable(instance.getId(), "endDate")).isEqualTo(requestedStartDate.plusDays(4).toString());
        assertThat(runtimeService.getVariable(instance.getId(), "durationDays")).isEqualTo(5);
        assertThat(runtimeService.getVariable(instance.getId(), "remainingVacationDays")).isEqualTo(30);
        assertThat(runtimeService.getVariable(instance.getId(), "validationPassed")).isEqualTo(true);

        Task managerTask = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("managers")
                .singleResult();
        assertThat(managerTask).isNotNull();

        // Story 10-3: set task-local approvalComment before completing
        taskService.claim(managerTask.getId(), "bob");
        taskService.setVariableLocal(managerTask.getId(), "approvalComment", "Approved for annual leave");
        taskService.complete(managerTask.getId(), Map.of("approved", true));

        // Story 10-3: approvalComment is task-local — not visible as process variable
        assertThat(runtimeService.getVariables(instance.getId())).doesNotContainKey("approvalComment");

        // Story 10-3: approvalComment is in history for the task
        HistoricVariableInstance hvi = historyService.createHistoricVariableInstanceQuery()
                .taskIdIn(managerTask.getId())
                .variableName("approvalComment")
                .singleResult();
        assertThat(hvi).isNotNull();
        assertThat(hvi.getValue()).isEqualTo("Approved for annual leave");

        Task hrTask = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("hr")
                .singleResult();
        assertThat(hrTask).isNotNull();

        taskService.claim(hrTask.getId(), "carol");
        taskService.complete(hrTask.getId());

        assertThat(activeProcessInstances(instance.getId())).isZero();
        assertThat(readBalance("alice")).isEqualTo(25);
        assertThat(historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("leaveStatus")
                .singleResult().getValue()).isEqualTo("APPROVED");

        // Story 10-3: historic remainingVacationDays (at start) greater than post-deduction balance
        HistoricVariableInstance historicBalance = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("remainingVacationDays")
                .singleResult();
        assertThat(historicBalance).isNotNull();
        assertThat((Integer) historicBalance.getValue()).isGreaterThan(readBalance("alice"));

        // Story 10-2: assert Task_ManagerReview was completed (history)
        assertThat(historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instance.getId())
                .activityId("Task_ManagerReview")
                .finished()
                .count()).isEqualTo(1);
    }

    @Test
    void rejectionPath_managerRejects_employeeIsNotified() {
        ProcessInstance instance = startLeaveRequest("alice", LocalDate.now().plusDays(1), 2);

        Task managerTask = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("managers")
                .singleResult();
        assertThat(managerTask).isNotNull();

        taskService.claim(managerTask.getId(), "bob");
        taskService.complete(managerTask.getId(), Map.of("approved", false));

        Task employeeTask = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("employees")
                .singleResult();
        assertThat(employeeTask).isNotNull();
        assertThat(employeeTask.getName()).isEqualTo("Employee Notified of Rejection");

        taskService.claim(employeeTask.getId(), "alice");
        taskService.complete(employeeTask.getId());

        assertThat(activeProcessInstances(instance.getId())).isZero();
        assertThat(readBalance("alice")).isEqualTo(30);
    }

    @Test
    void requestWithPastStartDate_isRejectedBeforeManagerReview() {
        ProcessInstance instance = startLeaveRequest("alice", LocalDate.now().minusDays(1), 3);

        assertThat(taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("managers")
                .singleResult()).isNull();

        Task employeeTask = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("employees")
                .singleResult();
        assertThat(employeeTask).isNotNull();
        assertThat(runtimeService.getVariable(instance.getId(), "validationPassed")).isEqualTo(false);
        assertThat(runtimeService.getVariable(instance.getId(), "rejectionReason"))
                .isEqualTo("Start date must be today or later.");
        assertThat(runtimeService.getVariable(instance.getId(), "remainingVacationDays")).isEqualTo(30);

        taskService.claim(employeeTask.getId(), "alice");
        taskService.complete(employeeTask.getId());

        assertThat(activeProcessInstances(instance.getId())).isZero();
        assertThat(readBalance("alice")).isEqualTo(30);
    }

    @Test
    void secondRequest_usesPersistedBalanceAndRejectsWhenDurationExceedsRemainingDays() {
        ProcessInstance approvedRequest = startLeaveRequest("alice", LocalDate.now().plusDays(3), 8);

        Task firstManagerTask = taskService.createTaskQuery()
                .processInstanceId(approvedRequest.getId())
                .taskCandidateGroup("managers")
                .singleResult();
        assertThat(firstManagerTask).isNotNull();
        taskService.claim(firstManagerTask.getId(), "bob");
        taskService.complete(firstManagerTask.getId(), Map.of("approved", true));

        Task firstHrTask = taskService.createTaskQuery()
                .processInstanceId(approvedRequest.getId())
                .taskCandidateGroup("hr")
                .singleResult();
        assertThat(firstHrTask).isNotNull();
        taskService.claim(firstHrTask.getId(), "carol");
        taskService.complete(firstHrTask.getId());

        assertThat(readBalance("alice")).isEqualTo(22);

        ProcessInstance rejectedRequest = startLeaveRequest("alice", LocalDate.now().plusDays(10), 23);

        assertThat(taskService.createTaskQuery()
                .processInstanceId(rejectedRequest.getId())
                .taskCandidateGroup("managers")
                .singleResult()).isNull();
        assertThat(runtimeService.getVariable(rejectedRequest.getId(), "remainingVacationDays")).isEqualTo(22);
        assertThat(runtimeService.getVariable(rejectedRequest.getId(), "validationPassed")).isEqualTo(false);
        assertThat(runtimeService.getVariable(rejectedRequest.getId(), "rejectionReason"))
                .isEqualTo("Requested 23 vacation days exceeds the remaining balance of 22 day(s).");

        Task employeeTask = taskService.createTaskQuery()
                .processInstanceId(rejectedRequest.getId())
                .taskCandidateGroup("employees")
                .singleResult();
        assertThat(employeeTask).isNotNull();
        taskService.claim(employeeTask.getId(), "alice");
        taskService.complete(employeeTask.getId());

        assertThat(activeProcessInstances(rejectedRequest.getId())).isZero();
        assertThat(readBalance("alice")).isEqualTo(22);
    }

    private ProcessInstance startLeaveRequest(String userId, LocalDate startDate, int durationDays) {
        return startLeaveRequest(userId, startDate, durationDays, Map.of());
    }

    private ProcessInstance startLeaveRequest(String userId, LocalDate startDate, int durationDays,
                                               Map<String, Object> extraVariables) {
        identityService.setAuthenticatedUserId(userId);
        try {
            java.util.HashMap<String, Object> vars = new java.util.HashMap<>();
            vars.put("startDate", startDate.toString());
            vars.put("durationDays", durationDays);
            vars.putAll(extraVariables);
            return runtimeService.startProcessInstanceByKey("leave-request", vars);
        } finally {
            identityService.clearAuthentication();
        }
    }

    private long activeProcessInstances(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count();
    }

    private int readBalance(String employeeId) {
        Integer balance = jdbcTemplate.queryForObject(
                "SELECT remaining_vacation_days FROM employee_vacation_balance WHERE employee_id = ?",
                Integer.class,
                employeeId
        );
        assertThat(balance).isNotNull();
        return balance;
    }
}
