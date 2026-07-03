package org.operaton.examples.spinjson;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.history.HistoricVariableInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LoanApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    @Test
    void loanApplicationIsProcessedWithJsonVariable() {
        LoanApplication app = new LoanApplication("Alice Smith", 10000.0, 36, "Home renovation");

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "loan-application",
            Variables.createVariables()
                .putValueTyped("application",
                    Variables.objectValue(app)
                        .serializationDataFormat("application/json")
                        .create()));

        completeReviewTask(instance);

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);

        assertThat(historicVariable(instance, "applicationValid")).isEqualTo(Boolean.TRUE);
        assertThat((Double) historicVariable(instance, "annualInterestRate")).isGreaterThan(5.0);
    }

    @Test
    void jsonVariableIsDeserializedCorrectlyInDelegate() {
        LoanApplication app = new LoanApplication("Bob Jones", 50000.0, 60, "Business expansion");

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "loan-application",
            Variables.createVariables()
                .putValueTyped("application",
                    Variables.objectValue(app)
                        .serializationDataFormat("application/json")
                        .create()));

        completeReviewTask(instance);

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat((Double) historicVariable(instance, "monthlyPayment")).isGreaterThan(0.0);
    }

    @Test
    void reviewOfferUserTaskIsAssignableToLoanOfficers() {
        LoanApplication app = new LoanApplication("Carol White", 20000.0, 24, "Car purchase");

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "loan-application",
            Variables.createVariables()
                .putValueTyped("application",
                    Variables.objectValue(app)
                        .serializationDataFormat("application/json")
                        .create()));

        // Verify the review task appeared and is visible to loanOfficers group
        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskDefinitionKey("UserTask_ReviewOffer")
            .taskCandidateGroup("loanOfficers")
            .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getName()).isEqualTo("Review offer");

        // Carol (loan officer) claims and completes it
        taskService.claim(task.getId(), "carol");
        taskService.complete(task.getId());

        HistoricProcessInstance historic = historicInstance(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
    }

    private void completeReviewTask(ProcessInstance instance) {
        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskDefinitionKey("UserTask_ReviewOffer")
            .singleResult();
        assertThat(task).as("Review offer task must exist").isNotNull();
        taskService.complete(task.getId());
    }

    private HistoricProcessInstance historicInstance(ProcessInstance instance) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
    }

    private Object historicVariable(ProcessInstance instance, String name) {
        HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName(name)
            .singleResult();
        return var == null ? null : var.getValue();
    }
}
