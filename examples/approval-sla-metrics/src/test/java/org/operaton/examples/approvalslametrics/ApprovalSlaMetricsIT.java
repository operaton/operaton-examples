package org.operaton.examples.approvalslametrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "demo.load-generator.enabled=false")
@Testcontainers
class ApprovalSlaMetricsIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    ProcessEngine processEngine;

    @Autowired
    IdentityService identityService;

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void seedsGroupsAndUsers() {
        assertThat(identityService.createGroupQuery().groupId("managers").count()).isEqualTo(1);
        assertThat(identityService.createGroupQuery().groupId("directors").count()).isEqualTo(1);
        assertThat(identityService.createUserQuery().userId("alice").count()).isEqualTo(1);
        assertThat(identityService.createUserQuery().userId("bob").count()).isEqualTo(1);
        assertThat(identityService.createUserQuery().userId("demo").count()).isEqualTo(1);
    }

    @Test
    void routingDecisionMapsAmountToTier() {
        var ds = processEngine.getDecisionService();

        var auto = ds.evaluateDecisionByKey("purchase-requisition-routing")
                .variables(Variables.putValue("amount", 500.0)).evaluate().getSingleResult();
        assertThat((String) auto.getEntry("approvalTier")).isEqualTo("auto");
        assertThat((String) auto.getEntry("slaDuration")).isEqualTo("PT0S");

        var manager = ds.evaluateDecisionByKey("purchase-requisition-routing")
                .variables(Variables.putValue("amount", 5000.0)).evaluate().getSingleResult();
        assertThat((String) manager.getEntry("approvalTier")).isEqualTo("manager");
        assertThat((String) manager.getEntry("slaDuration")).isEqualTo("PT5S");
        assertThat((String) manager.getEntry("approverGroup")).isEqualTo("managers");

        var director = ds.evaluateDecisionByKey("purchase-requisition-routing")
                .variables(Variables.putValue("amount", 25000.0)).evaluate().getSingleResult();
        assertThat((String) director.getEntry("approvalTier")).isEqualTo("director");
        assertThat((String) director.getEntry("slaDuration")).isEqualTo("PT2S");
        assertThat((String) director.getEntry("approverGroup")).isEqualTo("directors");
    }

    @Test
    void processAndDecisionAreDeployed() {
        assertThat(processEngine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("purchase-requisition-approval").count()).isEqualTo(1);
    }

    @Test
    void autoTierApprovesWithoutUserTask() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "purchase-requisition-approval",
                org.operaton.bpm.engine.variable.Variables
                        .putValue("amount", 500.0).putValue("requesterId", "emp-1"));
        // < 1000 auto-approves synchronously: no active instance, no user task
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId()).count())
                .isEqualTo(0);
        assertThat(meterRegistry.find("requisitions_total")
                .tag("tier", "auto").tag("outcome", "approved").counter().count())
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void managerTierApprovalRecordsWaitAndOutcome() {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "purchase-requisition-approval",
                org.operaton.bpm.engine.variable.Variables
                        .putValue("amount", 5000.0).putValue("requesterId", "emp-2"));

        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("approve-requisition").singleResult();
        assertThat(task).isNotNull();
        assertThat(meterRegistry.find("approvals_in_progress").tag("tier", "manager").gauge().value())
                .isGreaterThanOrEqualTo(1.0);

        taskService.complete(task.getId(),
                org.operaton.bpm.engine.variable.Variables.putValue("approved", true));

        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId()).count())
                .isEqualTo(0);
        assertThat(meterRegistry.find("approval_wait_seconds")
                .tag("tier", "manager").tag("outcome", "approved").timer().count())
                .isGreaterThanOrEqualTo(1L);
        assertThat(meterRegistry.find("requisitions_total")
                .tag("tier", "manager").tag("outcome", "approved").counter().count())
                .isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void directorTierBreachesSlaWithoutCancellingTask() {
        double before = breachCount();
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "purchase-requisition-approval",
                org.operaton.bpm.engine.variable.Variables
                        .putValue("amount", 25000.0).putValue("requesterId", "emp-3"));

        // director SLA = PT2S; the boundary timer must fire and bump the breach counter
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(breachCount()).isGreaterThan(before));

        // non-interrupting: the approval task is still open
        Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId()).taskDefinitionKey("approve-requisition").singleResult();
        assertThat(task).as("task remains open after breach").isNotNull();

        // complete it to let the instance finish
        taskService.complete(task.getId(),
                org.operaton.bpm.engine.variable.Variables.putValue("approved", true));
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId()).count())
                .isEqualTo(0);
    }

    private double breachCount() {
        var counter = meterRegistry.find("approval_sla_breaches_total").tag("tier", "director").counter();
        return counter == null ? 0.0 : counter.count();
    }
}
