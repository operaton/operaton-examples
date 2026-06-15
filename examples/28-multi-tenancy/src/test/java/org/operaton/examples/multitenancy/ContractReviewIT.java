package org.operaton.examples.multitenancy;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContractReviewIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Test
    void tenantARejectsNonStandardContracts() {
        // Tenant A has strict rules: only "standard" contracts are compliant
        ProcessInstance instance = runtimeService.createProcessInstanceByKey("contract-review")
            .processDefinitionTenantId("tenant-a")
            .setVariable("contractType", "enterprise")
            .execute();

        // Should be rejected (enterprise not compliant for tenant-a) — process ended immediately
        var activeInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(activeInstance).isNull();

        // No pending tasks
        var task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(task).isNull();
    }

    @Test
    void tenantBAcceptsEnterpriseContracts() {
        // Tenant B: enterprise contracts are compliant
        ProcessInstance instance = runtimeService.createProcessInstanceByKey("contract-review")
            .processDefinitionTenantId("tenant-b")
            .setVariable("contractType", "enterprise")
            .execute();

        // Should be waiting at legal review
        var task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("Task_LegalReview");

        // Bob is in tenant-b legal group, can claim and complete
        taskService.claim(task.getId(), "bob");
        taskService.complete(task.getId());

        var active = runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(active).isNull();
    }

    @Test
    void tenantsHaveIsolatedProcessInstances() {
        // Start instances for both tenants with compliant contracts
        ProcessInstance instanceA = runtimeService.createProcessInstanceByKey("contract-review")
            .processDefinitionTenantId("tenant-a")
            .setVariable("contractType", "standard")
            .execute();

        ProcessInstance instanceB = runtimeService.createProcessInstanceByKey("contract-review")
            .processDefinitionTenantId("tenant-b")
            .setVariable("contractType", "standard")
            .execute();

        // Tenant A's task is only visible to tenant A
        var tasksA = taskService.createTaskQuery()
            .processInstanceId(instanceA.getId())
            .tenantIdIn("tenant-a")
            .list();
        assertThat(tasksA).hasSize(1);
        assertThat(tasksA.get(0).getProcessInstanceId()).isEqualTo(instanceA.getId());

        // Tenant B's task is only visible to tenant B
        var tasksB = taskService.createTaskQuery()
            .processInstanceId(instanceB.getId())
            .tenantIdIn("tenant-b")
            .list();
        assertThat(tasksB).hasSize(1);
        assertThat(tasksB.get(0).getProcessInstanceId()).isEqualTo(instanceB.getId());

        // Clean up — complete both tasks so the process ends cleanly
        taskService.complete(tasksA.get(0).getId());
        taskService.complete(tasksB.get(0).getId());
    }
}
