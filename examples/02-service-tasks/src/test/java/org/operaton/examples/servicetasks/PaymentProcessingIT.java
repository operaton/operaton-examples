package org.operaton.examples.servicetasks;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
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

@SpringBootTest
@Testcontainers
class PaymentProcessingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired ManagementService managementService;

    @Test
    void paymentIsProcessedSuccessfully() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "payment-processing",
            Map.of("amount", 99.99, "cardNumber", "4111111111111111"));

        // After start: sync EnrichPayment runs immediately; process then stops at the
        // async ChargePayment job. The job executor picks it up in a new transaction.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            HistoricProcessInstance historic = historicInstance(instance);
            assertThat(historic).isNotNull();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_PaymentProcessed");
        });
        assertThat(historicVariable(instance, "enriched")).isEqualTo(true);
        assertThat(historicVariable(instance, "chargeId")).asString().startsWith("CHG-");
    }

    @Test
    void paymentDeclinedTriggersBoundaryEvent() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "payment-processing",
            Map.of("amount", 99.99, "cardNumber", "4111111111111111", "simulateDecline", true));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            HistoricProcessInstance historic = historicInstance(instance);
            assertThat(historic).isNotNull();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_PaymentDeclined");
        });
    }

    @Test
    void transientErrorDecrementsJobRetries() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "payment-processing",
            Map.of("amount", 99.99, "cardNumber", "4111111111111111", "simulateTransientError", true));

        // Wait for the async job to be created (engine persisted the job after asyncBefore)
        Job job = await().atMost(Duration.ofSeconds(5))
            .until(() -> managementService.createJobQuery()
                    .processInstanceId(instance.getId()).singleResult(),
                j -> j != null);

        // The BPMN configures R3 → initial retries must be 3
        assertThat(job.getRetries()).isEqualTo(3);

        // Manually execute the job — ChargePaymentDelegate throws RuntimeException
        try {
            managementService.executeJob(job.getId());
        } catch (RuntimeException ignored) {
            // Expected: job failed; engine decrements retries
        }

        // After one failure retries go 3 → 2
        Job retriedJob = managementService.createJobQuery()
            .jobId(job.getId()).singleResult();
        assertThat(retriedJob.getRetries()).isEqualTo(2);
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
