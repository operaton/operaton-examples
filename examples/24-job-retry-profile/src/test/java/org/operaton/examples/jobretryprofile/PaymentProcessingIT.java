package org.operaton.examples.jobretryprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "operaton.bpm.job-execution.enabled=false", classes = JobRetryProfileApplication.class)
@Testcontainers
class PaymentProcessingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ManagementService managementService;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired PaymentServiceDelegate paymentServiceDelegate;

    @BeforeEach
    void resetDelegate() {
        paymentServiceDelegate.configureToFailForAttempts(0);
    }

    @Test
    void successfulPaymentOnFirstAttempt() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("payment-processing");

        Job job = managementService.createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(job).isNotNull();
        assertThat(job.getRetries()).isEqualTo(3); // R3 from BPMN

        managementService.executeJob(job.getId());

        // Process should have completed
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);

        String code = (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("paymentConfirmationCode")
                .singleResult()
                .getValue();
        assertThat(code).startsWith("PAY-");
    }

    @Test
    void retriesDecrementOnTransientFailure() {
        paymentServiceDelegate.configureToFailForAttempts(2); // fail first 2 attempts

        ProcessInstance instance = runtimeService.startProcessInstanceByKey("payment-processing");

        Job job = managementService.createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(job.getRetries()).isEqualTo(3);

        // First attempt fails
        try { managementService.executeJob(job.getId()); } catch (Exception ignored) {}

        Job afterFirstFailure = managementService.createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(afterFirstFailure.getRetries()).isEqualTo(2); // decremented

        // Second attempt fails
        try { managementService.executeJob(afterFirstFailure.getId()); } catch (Exception ignored) {}

        Job afterSecondFailure = managementService.createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(afterSecondFailure.getRetries()).isEqualTo(1); // decremented again

        // Third attempt succeeds
        managementService.executeJob(afterSecondFailure.getId());

        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
    }
}
