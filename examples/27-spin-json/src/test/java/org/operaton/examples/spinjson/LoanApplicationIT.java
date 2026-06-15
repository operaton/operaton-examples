package org.operaton.examples.spinjson;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.history.HistoricVariableInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
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
    @Autowired HistoryService historyService;

    @Test
    void loanApplicationIsProcessedWithJsonVariable() {
        LoanApplication app = new LoanApplication("Alice Smith", 10000.0, 36, "Home renovation");

        // Store the domain object as a JSON variable using Spin serialization
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "loan-application",
                Variables.createVariables()
                        .putValueTyped("application",
                                Variables.objectValue(app)
                                        .serializationDataFormat("application/json")
                                        .create()));

        // Process should have completed (no wait states)
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);

        // Verify applicationValid was set to true by the validate delegate
        HistoricVariableInstance validVar = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("applicationValid")
                .singleResult();
        assertThat(validVar.getValue()).isEqualTo(Boolean.TRUE);

        // Verify annual interest rate was calculated
        HistoricVariableInstance rateVar = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("annualInterestRate")
                .singleResult();
        assertThat((Double) rateVar.getValue()).isGreaterThan(5.0);
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

        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);

        // Monthly payment should have been calculated (non-zero)
        HistoricVariableInstance paymentVar = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("monthlyPayment")
                .singleResult();
        assertThat((Double) paymentVar.getValue()).isGreaterThan(0.0);
    }
}
