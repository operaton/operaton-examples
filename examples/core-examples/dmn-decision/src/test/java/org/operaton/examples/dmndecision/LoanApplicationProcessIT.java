package org.operaton.examples.dmndecision;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
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
class LoanApplicationProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void excellentCreditScoreAndSmallAmountIsApproved() {
        ProcessInstance instance = startProcess(750, 10_000);
        assertEndEvent(instance, "EndEvent_Approved");
        assertThat(decisionOutput(instance)).isEqualTo("approved");
    }

    @Test
    void poorCreditScoreIsDeclined() {
        ProcessInstance instance = startProcess(450, 5_000);
        assertEndEvent(instance, "EndEvent_Declined");
        assertThat(decisionOutput(instance)).isEqualTo("declined");
    }

    @Test
    void mediumCreditScoreWithLargeAmountRequiresReview() {
        ProcessInstance instance = startProcess(620, 80_000);
        assertEndEvent(instance, "EndEvent_Review");
        assertThat(decisionOutput(instance)).isEqualTo("review");
    }

    @Test
    void goodCreditScoreWithMediumAmountIsApproved() {
        ProcessInstance instance = startProcess(700, 25_000);
        assertEndEvent(instance, "EndEvent_Approved");
        assertThat(decisionOutput(instance)).isEqualTo("approved");
    }

    private ProcessInstance startProcess(int creditScore, int requestedAmount) {
        return runtimeService.startProcessInstanceByKey("loan-application",
            Map.of("creditScore", creditScore, "requestedAmount", requestedAmount));
    }

    private void assertEndEvent(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo(endEventId);
    }

    private Object decisionOutput(ProcessInstance instance) {
        return historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName("loanDecision")
            .singleResult()
            .getValue();
    }
}
