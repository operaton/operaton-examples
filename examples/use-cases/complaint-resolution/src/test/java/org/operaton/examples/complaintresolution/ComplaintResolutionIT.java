package org.operaton.examples.complaintresolution;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ComplaintResolutionIT {

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
            .processDefinitionKey("complaint-resolution")
            .count()).isEqualTo(1);
    }

    @Test
    void happyPath_withinAuthority_resolvesDirectly() {
        // severity=low, refund=200 (within 500 threshold) — no escalation
        var pi = runtimeService.startProcessInstanceByKey(
            "complaint-resolution",
            "COMP-001",
            Variables.createVariables()
                .putValue("complaintId", "COMP-001")
                .putValue("customer", "Alice")
                .putValue("category", "billing")
                .putValue("severity", "low")
                .putValue("requestedRefund", 200.0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ComplaintResolved");
        });

        // No refund or specialist escalation
        assertThat(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("refundApproved").singleResult()).isNull();
        assertThat(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("specialistHandoff").singleResult()).isNull();
    }

    @Test
    void nonInterruptingEscalation_exceedsAuthority_approvesInParallel() {
        // severity=low, refund=800 (exceeds 500 threshold) — non-interrupting escalation
        // manager-approval token runs in parallel, subprocess continues normally
        var pi = runtimeService.startProcessInstanceByKey(
            "complaint-resolution",
            "COMP-002",
            Variables.createVariables()
                .putValue("complaintId", "COMP-002")
                .putValue("customer", "Bob")
                .putValue("category", "service")
                .putValue("severity", "low")
                .putValue("requestedRefund", 800.0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            // Manager-approval token runs after the main path in synchronous execution
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_RefundApproved");
        });

        // also verify that the normal subprocess completion path ran concurrently
        org.operaton.bpm.engine.history.HistoricActivityInstance closedEnd = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(pi.getId())
            .activityId("EndEvent_ComplaintResolved")
            .singleResult();
        assertThat(closedEnd).as("EndEvent_ComplaintResolved should have been reached (non-interrupting: subprocess runs to completion)").isNotNull();

        // Manager-approval path must have run
        var refundApproved = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("refundApproved").singleResult();
        assertThat(refundApproved).isNotNull();
        assertThat((Boolean) refundApproved.getValue()).isTrue();

        assertThat(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("specialistHandoff").singleResult()).isNull();
    }

    @Test
    void interruptingEscalation_highSeverity_routesToSpecialist() {
        // severity=high — interrupting escalation cancels subprocess, routes to specialist
        var pi = runtimeService.startProcessInstanceByKey(
            "complaint-resolution",
            "COMP-003",
            Variables.createVariables()
                .putValue("complaintId", "COMP-003")
                .putValue("customer", "Carol")
                .putValue("category", "safety")
                .putValue("severity", "high")
                .putValue("requestedRefund", 1500.0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_EscalatedToSpecialist");
        });

        // Specialist handoff must have run
        var specialistHandoff = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("specialistHandoff").singleResult();
        assertThat(specialistHandoff).isNotNull();
        assertThat((Boolean) specialistHandoff.getValue()).isTrue();

        // No refund approval — subprocess was cancelled before reaching refund logic
        assertThat(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("refundApproved").singleResult()).isNull();
    }
}
