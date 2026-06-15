package org.operaton.examples.processmigration;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.RepositoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.migration.MigrationPlan;
import org.operaton.bpm.engine.repository.Deployment;
import org.operaton.bpm.engine.repository.ProcessDefinition;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LoanApplicationMigrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired RepositoryService repositoryService;
    @Autowired ManagementService managementService;

    @Test
    void migrateInstanceFromV1ToV2() {
        // Deploy v1
        Deployment v1 = repositoryService.createDeployment()
            .addClasspathResource("loan-application-v1.bpmn")
            .name("v1")
            .deploy();

        ProcessDefinition defV1 = repositoryService.createProcessDefinitionQuery()
            .deploymentId(v1.getId())
            .singleResult();

        // Start instance on v1 — it stops at underwriter review
        ProcessInstance instance = runtimeService.startProcessInstanceById(defV1.getId());

        Task taskV1 = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(taskV1).isNotNull();
        assertThat(taskV1.getTaskDefinitionKey()).isEqualTo("Task_UnderwriterReview");
        assertThat(taskV1.getProcessDefinitionId()).isEqualTo(defV1.getId());

        // Deploy v2
        Deployment v2 = repositoryService.createDeployment()
            .addClasspathResource("loan-application-v2.bpmn")
            .name("v2")
            .deploy();

        ProcessDefinition defV2 = repositoryService.createProcessDefinitionQuery()
            .deploymentId(v2.getId())
            .singleResult();

        // Create migration plan: map equal activity IDs automatically
        MigrationPlan plan = runtimeService.createMigrationPlan(defV1.getId(), defV2.getId())
            .mapEqualActivities()
            .build();

        // Migrate the running instance
        runtimeService.newMigration(plan)
            .processInstanceIds(instance.getId())
            .execute();

        // Instance should still be at Task_UnderwriterReview, but now on v2
        Task taskV2 = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(taskV2).isNotNull();
        assertThat(taskV2.getTaskDefinitionKey()).isEqualTo("Task_UnderwriterReview");
        assertThat(taskV2.getProcessDefinitionId()).isEqualTo(defV2.getId());

        // Complete the task — process should end
        taskService.complete(taskV2.getId());

        var active = runtimeService.createProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(active).isNull(); // process completed
    }

    @Test
    void newInstanceOnV2HasFraudCheck() {
        // Verify that new instances on v2 go through the fraud check step
        Deployment v2 = repositoryService.createDeployment()
            .addClasspathResource("loan-application-v2.bpmn")
            .name("v2-direct")
            .deploy();

        ProcessDefinition defV2 = repositoryService.createProcessDefinitionQuery()
            .deploymentId(v2.getId())
            .singleResult();

        ProcessInstance instance = runtimeService.startProcessInstanceById(defV2.getId());

        // Fraud check completes synchronously; instance waits at user task
        var task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("Task_UnderwriterReview");

        // Check that fraudRisk variable was set by the fraud check delegate
        var fraudRisk = runtimeService.getVariable(instance.getId(), "fraudRisk");
        assertThat(fraudRisk).isEqualTo("low");

        taskService.complete(task.getId());
    }
}
