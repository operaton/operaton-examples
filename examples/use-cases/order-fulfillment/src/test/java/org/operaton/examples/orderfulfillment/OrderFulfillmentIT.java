package org.operaton.examples.orderfulfillment;

import org.junit.jupiter.api.*;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ManagementService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.SuspendedEntityInteractionException;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "operaton.bpm.job-execution.enabled=false")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderFulfillmentIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
            .withMappingFromResource("inventory-in-stock", "wiremock/mappings/inventory-in-stock.json")
            .withMappingFromResource("inventory-out-of-stock", "wiremock/mappings/inventory-out-of-stock.json")
            .withMappingFromResource("payment-success", "wiremock/mappings/payment-success.json")
            .withMappingFromResource("notify-customer", "wiremock/mappings/notify-customer.json")
            .withMappingFromResource("notify-backorder", "wiremock/mappings/notify-backorder.json")
            .waitingFor(Wait.forHttp("/__admin/mappings"));

    @DynamicPropertySource
    static void wireMockProperties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
        registry.add("inventory.service.url", () -> baseUrl);
        registry.add("payment.service.url", () -> baseUrl);
        registry.add("notification.service.url", () -> baseUrl);
    }

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private HistoryService historyService;

    @Test
    @Order(1)
    void processDefinitionIsDeployed() {
        long count = processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("order-fulfillment")
                .count();
        assertEquals(1, count, "order-fulfillment process must be deployed");
    }

    @Test
    @Order(2)
    void inStockPath_createsWarehouseTask() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        TaskService taskService = processEngine.getTaskService();

        // in-stock orderId (matches /inventory/.* → available: true)
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "order-fulfillment",
                Variables.putValue("orderId", "ORD-001"));

        // Payment task has asyncBefore — execute the job
        Job job = managementService.createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        if (job != null) {
            managementService.executeJob(job.getId());
        }

        // Warehouse pack & ship task should be created
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .taskCandidateGroup("warehouse")
                .list();
        assertFalse(tasks.isEmpty(), "Expected warehouse pack & ship task for in-stock order");

        // Dave completes the warehouse task
        taskService.claim(tasks.get(0).getId(), "dave");
        taskService.complete(tasks.get(0).getId());

        // Process should be complete
        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .count();
        assertEquals(0, activeCount, "No active instances should remain after in-stock path");
    }

    @Test
    @Order(3)
    void outOfStockPath_endsDirectly() {
        RuntimeService runtimeService = processEngine.getRuntimeService();

        // out-of-stock orderId (matches /inventory/out-of-stock-.* → available: false)
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "order-fulfillment",
                Variables.putValue("orderId", "out-of-stock-ORD-002"));

        // Process should end directly without any warehouse task
        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .count();
        assertEquals(0, activeCount, "No active instances should remain after out-of-stock path");
    }

    @Test
    @Order(4)
    void paymentFailurePath_endsAtFailedEvent() {
        RuntimeService runtimeService = processEngine.getRuntimeService();

        // Start process with simulatePaymentFailure = true (in-stock order so payment is reached)
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "order-fulfillment",
                Variables.createVariables()
                        .putValue("orderId", "ORD-003")
                        .putValue("simulatePaymentFailure", true));

        // The async job for Task_ChargePayment must be executed to trigger the BpmnError
        Job job = processEngine.getManagementService().createJobQuery()
                .processInstanceId(instance.getId())
                .singleResult();
        if (job != null) {
            processEngine.getManagementService().executeJob(job.getId());
        }

        // Process should have ended (via error boundary → EndEvent_Failed)
        long finishedCount = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .finished()
                .count();
        assertEquals(1, finishedCount, "Process should have finished via payment failure path");

        // orderStatus should be FAILED
        Object orderStatus = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName("orderStatus")
                .singleResult()
                .getValue();
        assertEquals("FAILED", orderStatus, "orderStatus should be FAILED");
    }

    @Test
    @Order(5)
    void suspendAndActivateProcessInstance() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        TaskService taskService = processEngine.getTaskService();

        // Use happy path (in-stock order)
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "order-fulfillment",
                Variables.putValue("orderId", "ORD-001"));

        // Suspend the process instance
        runtimeService.suspendProcessInstanceById(pi.getId());

        assertEquals(1, runtimeService.createProcessInstanceQuery().suspended()
                .processInstanceId(pi.getId()).count());

        Job job = managementService.createJobQuery().processInstanceId(pi.getId()).singleResult();
        if (job != null) {
            assertThrows(SuspendedEntityInteractionException.class,
                    () -> managementService.executeJob(job.getId()));
        }

        // Activate the process instance
        runtimeService.activateProcessInstanceById(pi.getId());

        assertEquals(1, runtimeService.createProcessInstanceQuery().active()
                .processInstanceId(pi.getId()).count());

        // Execute the payment job to move past the async step
        Job activeJob = managementService.createJobQuery().processInstanceId(pi.getId()).singleResult();
        if (activeJob != null) {
            managementService.executeJob(activeJob.getId());
        }

        // Clean up: complete the warehouse task so the process ends
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
        if (task != null) {
            taskService.claim(task.getId(), "dave");
            taskService.complete(task.getId());
        }
    }
}
