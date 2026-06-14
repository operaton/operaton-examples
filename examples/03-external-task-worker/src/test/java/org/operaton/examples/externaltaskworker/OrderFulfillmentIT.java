package org.operaton.examples.externaltaskworker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "operaton.worker.enabled=false"
)
@Testcontainers
class OrderFulfillmentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    ExternalTaskClient taskClient;

    @BeforeEach
    void startWorkers() {
        AtomicBoolean inventoryFailedOnce = new AtomicBoolean(false);

        taskClient = ExternalTaskClient.create()
            .baseUrl("http://localhost:" + port + "/engine-rest")
            .lockDuration(5_000)
            .build();

        taskClient.subscribe("inventory-check")
            .handler((task, service) -> {
                if (Boolean.TRUE.equals(task.getVariable("simulateOutOfStock"))) {
                    // Routing signal — not a retryable error
                    service.handleBpmnError(task, "OUT_OF_STOCK", "Item is out of stock");
                } else if (Boolean.TRUE.equals(task.getVariable("simulateOneFailure"))
                        && !inventoryFailedOnce.getAndSet(true)) {
                    // First call fails; retryTimeout=0 means immediate re-queue
                    service.handleFailure(task, "Transient error", "DB timeout", 2, 0L);
                } else {
                    service.complete(task, Map.of("reservationId", "RES-" + task.getId()));
                }
            })
            .open();

        taskClient.subscribe("arrange-shipping")
            .handler((task, service) ->
                service.complete(task, Map.of("trackingId", "TRK-" + task.getId())))
            .open();
    }

    @AfterEach
    void stopWorkers() {
        if (taskClient != null) {
            taskClient.stop();
        }
    }

    @Test
    void orderIsFulfilledSuccessfully() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("order-fulfillment",
            Map.of("orderId", "ORD-001", "sku", "WIDGET-42", "quantity", 2));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            HistoricProcessInstance historic = historicInstance(instance);
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_OrderFulfilled");
        });

        assertThat(historicVariable(instance, "reservationId")).asString().startsWith("RES-");
        assertThat(historicVariable(instance, "trackingId")).asString().startsWith("TRK-");
    }

    @Test
    void outOfStockItemRoutesThroughBoundaryEvent() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("order-fulfillment",
            Map.of("orderId", "ORD-002", "sku", "RARE-ITEM", "quantity", 1,
                   "simulateOutOfStock", true));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            HistoricProcessInstance historic = historicInstance(instance);
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_OrderBackordered");
        });
    }

    @Test
    void transientFailureIsRetriedAndEventuallySucceeds() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("order-fulfillment",
            Map.of("orderId", "ORD-003", "sku", "WIDGET-1", "quantity", 1,
                   "simulateOneFailure", true));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            HistoricProcessInstance historic = historicInstance(instance);
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_OrderFulfilled");
        });
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
