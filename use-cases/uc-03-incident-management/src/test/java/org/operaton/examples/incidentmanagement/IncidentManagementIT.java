package org.operaton.examples.incidentmanagement;

import org.junit.jupiter.api.*;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.impl.util.ClockUtil;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ContextConfiguration(initializers = IncidentManagementIT.WireMockInitializer.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncidentManagementIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
            .withMappingFromResource("close-ticket-stub.json")
            .withMappingFromResource("post-mortem-stub.json");

    static class WireMockInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            String baseUrl = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
            TestPropertyValues.of(
                    "ticket.service.url=" + baseUrl,
                    "notification.service.url=" + baseUrl
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired
    private ProcessEngine processEngine;

    @AfterEach
    void resetClock() {
        ClockUtil.reset();
    }

    @Test
    @Order(1)
    void processDefinitionIsDeployed() {
        long count = processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("incident-management")
                .count();
        assertEquals(1, count, "incident-management process must be deployed");
    }

    @Test
    @Order(2)
    void resolvedPath_firstLineTriage_closesTicket() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        TaskService taskService = processEngine.getTaskService();

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "incident-management",
                Variables.putValue("incidentId", "INC-001"));

        // Exactly one timer job must be registered
        long timerCount = processEngine.getManagementService()
                .createJobQuery()
                .processInstanceId(instance.getId())
                .timers()
                .count();
        assertEquals(1, timerCount, "Exactly one timer job must exist after process start");

        // Frank claims and resolves
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("first-line")
                .list();
        assertFalse(tasks.isEmpty(), "Expected first-line triage task");

        taskService.claim(tasks.get(0).getId(), "frank");
        taskService.complete(tasks.get(0).getId(),
                Variables.putValue("resolved", true));

        // Process should be complete
        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .count();
        assertEquals(0, activeCount, "No active instances should remain after resolved path");
    }

    @Test
    @Order(3)
    void timerFires_escalatesToSecondLine() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        TaskService taskService = processEngine.getTaskService();
        ManagementService managementService = processEngine.getManagementService();

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "incident-management",
                Variables.putValue("incidentId", "INC-002"));

        // Assert exactly one timer job exists
        long timerCount = managementService.createJobQuery()
                .processInstanceId(instance.getId())
                .timers()
                .count();
        assertEquals(1, timerCount, "Exactly one timer job must exist after process start");

        // Advance Operaton's internal clock past PT5S (no Thread.sleep)
        ClockUtil.setCurrentTime(new Date(System.currentTimeMillis() + 10_000));

        // Execute the due timer job
        managementService.createJobQuery()
                .processInstanceId(instance.getId())
                .timers()
                .list()
                .forEach(job -> managementService.executeJob(job.getId()));

        // Task should now be with second-line
        List<Task> secondLineTasks = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("second-line")
                .list();
        assertFalse(secondLineTasks.isEmpty(),
                "Expected second-line task after timer escalation");

        // Complete second-line task and post-mortem
        taskService.claim(secondLineTasks.get(0).getId(), "grace");
        taskService.complete(secondLineTasks.get(0).getId());

        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .count();
        assertEquals(0, activeCount, "No active instances should remain after escalation path");
    }

    @Test
    @Order(4)
    void signalEscalation_createsSecondLineTask_whileTriageRemainsActive() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        TaskService taskService = processEngine.getTaskService();

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "incident-management",
                Variables.putValue("incidentId", "INC-003"));

        String pid = instance.getId();

        // Verify triage task is active before signal
        List<Task> triageBefore = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey("Task_FirstLineTriage")
                .list();
        assertFalse(triageBefore.isEmpty(), "First-line triage task must exist before signal");

        // Send escalation signal scoped to this instance's execution
        org.operaton.bpm.engine.runtime.Execution signalExecution = runtimeService.createExecutionQuery()
                .processInstanceId(pid)
                .signalEventSubscriptionName("EscalationSignal")
                .singleResult();
        runtimeService.signalEventReceived("EscalationSignal", signalExecution.getId());

        // Triage task must still be active (non-interrupting)
        long triageCount = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey("Task_FirstLineTriage")
                .count();
        assertEquals(1, triageCount, "First-line triage task must remain active after signal");

        // Second-line task must have been created
        long secondLineCount = taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskDefinitionKey("Task_SecondLine")
                .count();
        assertEquals(1, secondLineCount, "Second-line task must be created after signal");

        // incidentPriority must be HIGH
        Object priority = runtimeService.getVariable(pid, "incidentPriority");
        assertEquals("HIGH", priority, "incidentPriority must be HIGH after signal escalation");
    }
}
