package org.operaton.examples.insuranceclaim;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class InsuranceClaimIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void processDefinitionIsDeployed() {
        assertThat(processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("insurance-claim")
            .count()).isEqualTo(1);
    }

    @Test
    void settlementDecisionMapsCorrectly() {
        var ds = processEngine.getDecisionService();

        // fraudSuspected → always reject regardless of other inputs
        var result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", true)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 500.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(0.0);

        // flood → reject regardless of amount or fraud
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "flood")
                .putValue("appraisedAmount", 5000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(0.0);

        // small collision (<=1000) → approve, full appraised amount
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 720.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("approve");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(720.0);

        // medium collision (<=50000) → approve at 80%
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 5000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("approve");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(4000.0);

        // large amount (>50000) → reject
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 60000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
    }

    @Test
    void happyPath_smallCollisionClaim_settlesWithAppraisedAmount() {
        var pi = runtimeService.startProcessInstanceByKey(
            "insurance-claim",
            "CLM-001",
            Variables.createVariables()
                .putValue("claimNumber", "CLM-001")
                .putValue("policyNumber", "POL-42")
                .putValue("claimType", "collision")
                .putValue("estimatedAmount", 800.0)
                .putValue("documentDeadline", "P14D"));

        runtimeService.correlateMessage("documentsReceived", "CLM-001", Map.of());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ClaimSettled");
        });

        var appraisedAmount = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("appraisedAmount").singleResult();
        assertThat(appraisedAmount).isNotNull();
        assertThat((Double) appraisedAmount.getValue()).isEqualTo(720.0);
    }

    @Test
    void rejectPath_floodClaim_isRejectedRegardlessOfAmount() {
        var pi = runtimeService.startProcessInstanceByKey(
            "insurance-claim",
            "CLM-002",
            Variables.createVariables()
                .putValue("claimNumber", "CLM-002")
                .putValue("policyNumber", "POL-43")
                .putValue("claimType", "flood")
                .putValue("estimatedAmount", 5000.0)
                .putValue("documentDeadline", "P14D"));

        runtimeService.correlateMessage("documentsReceived", "CLM-002", Map.of());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ClaimRejected");
        });
    }

    @Test
    void timeoutPath_noDocumentsReceived_closesClaimAfterDeadline() {
        var pi = runtimeService.startProcessInstanceByKey(
            "insurance-claim",
            "CLM-003",
            Variables.createVariables()
                .putValue("claimNumber", "CLM-003")
                .putValue("policyNumber", "POL-44")
                .putValue("claimType", "collision")
                .putValue("estimatedAmount", 500.0)
                .putValue("documentDeadline", "PT3S"));

        // Do NOT send the message — the timer (PT3S) should fire instead

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ClaimClosed");
        });
    }
}
