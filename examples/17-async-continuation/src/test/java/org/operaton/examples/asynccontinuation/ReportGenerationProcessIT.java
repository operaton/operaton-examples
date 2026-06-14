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

@SpringBootTest(properties = "operaton.bpm.job-execution.enabled=false")
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
    void asyncTasksRequireJobExecutionToProgress() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "report-generation", Map.of("failTwice", false));

        // Process is paused — 3 async tasks means 1 job pending at first task
        assertThat(managementService.createJobQuery()
            .processInstanceId(instance.getId()).count()).isEqualTo(1);

        // Drive all three jobs manually
        executeAllJobs(instance.getId());

        // All done
        assertCompleted(instance, "EndEvent_ReportReady");
        assertVariable(instance.getId(), "dataFetched", true);
        assertVariable(instance.getId(), "dataProcessed", true);
        assertVariable(instance.getId(), "reportStored", true);
    }

    @Test
    void transientFailureIsRetriedUntilSuccess() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "report-generation", Map.of("failTwice", true));

        // Execute Task_FetchData job (succeeds)
        Job fetchJob = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        managementService.executeJob(fetchJob.getId());

        // Now at Task_ProcessData — will fail twice before succeeding
        // First attempt — fails, retries=2 remaining
        Job processJob1 = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(processJob1.getRetries()).isEqualTo(3);

        // Execute and expect failure — job gets retries decremented
        try { managementService.executeJob(processJob1.getId()); } catch (Exception ignored) {}
        Job afterFail1 = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(afterFail1.getRetries()).isEqualTo(2);

        // Second attempt — fails again
        try { managementService.executeJob(afterFail1.getId()); } catch (Exception ignored) {}
        Job afterFail2 = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(afterFail2.getRetries()).isEqualTo(1);

        // Third attempt — succeeds
        managementService.executeJob(afterFail2.getId());

        // Now execute remaining Task_StoreReport job
        Job storeJob = managementService.createJobQuery()
            .processInstanceId(instance.getId()).singleResult();
        managementService.executeJob(storeJob.getId());

        assertCompleted(instance, "EndEvent_ReportReady");
    }

    @Test
    void jobsAreVisibleBeforeExecution() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "report-generation", Map.of("failTwice", false));

        // One job pending (at Task_FetchData)
        assertThat(managementService.createJobQuery()
            .processInstanceId(instance.getId()).count()).isEqualTo(1);

        // Process instance still "active" (not yet completed)
        assertThat(runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId()).count()).isEqualTo(1);
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
