package org.operaton.examples.asynccontinuation;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "operaton.bpm.job-execution.enabled=false", classes = AsyncContinuationApplication.class)
@Testcontainers
class ReportGenerationProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired ManagementService managementService;

    @Test
    void asyncTasksRequireManualJobExecutionToProgress() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "report-generation", Map.of("failTwice", false));

        // One job pending at Task_FetchData
        assertThat(managementService.createJobQuery()
            .processInstanceId(instance.getId()).count()).isEqualTo(1);

        // Drive all jobs to completion
        executeAllJobs(instance.getId());

        assertCompleted(instance, "EndEvent_ReportReady");
        assertVariable(instance.getId(), "dataFetched", true);
        assertVariable(instance.getId(), "dataProcessed", true);
        assertVariable(instance.getId(), "reportStored", true);
    }

    @Test
    void transientFailureIsRetriedUntilSuccess() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "report-generation", Map.of("failTwice", true));

        // Execute Task_FetchData (succeeds)
        Job fetchJob = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        managementService.executeJob(fetchJob.getId());

        // Task_ProcessData job — will fail twice (retries=3 configured)
        Job processJob = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(processJob.getRetries()).isEqualTo(3);

        // Attempt 1 — fails, retries decrement
        try { managementService.executeJob(processJob.getId()); } catch (Exception ignored) {}
        Job afterFail1 = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(afterFail1.getRetries()).isEqualTo(2);

        // Attempt 2 — fails again
        try { managementService.executeJob(afterFail1.getId()); } catch (Exception ignored) {}
        Job afterFail2 = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(afterFail2.getRetries()).isEqualTo(1);

        // Attempt 3 — succeeds
        managementService.executeJob(afterFail2.getId());

        // Execute Task_StoreReport
        Job storeJob = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        managementService.executeJob(storeJob.getId());

        assertCompleted(instance, "EndEvent_ReportReady");
    }

    @Test
    void processIsActiveButNotYetCompletedWhileJobsPending() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "report-generation", Map.of("failTwice", false));

        // Active instance exists
        assertThat(runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId()).count()).isEqualTo(1);
        // One job pending
        assertThat(managementService.createJobQuery()
            .processInstanceId(instance.getId()).count()).isEqualTo(1);
        // Not in history as completed yet
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(h.getEndTime()).isNull();
    }

    private void executeAllJobs(String processInstanceId) {
        while (true) {
            Job job = managementService.createJobQuery()
                .processInstanceId(processInstanceId).singleResult();
            if (job == null) break;
            managementService.executeJob(job.getId());
        }
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance h = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(h.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(h.getEndActivityId()).isEqualTo(endEventId);
    }

    private void assertVariable(String processInstanceId, String name, Object expected) {
        var v = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId).variableName(name).singleResult();
        assertThat(v).isNotNull();
        assertThat(v.getValue()).isEqualTo(expected);
    }
}
