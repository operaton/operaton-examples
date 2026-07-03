package org.operaton.examples.signalevents;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SignalEventsIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    HistoryService historyService;

    @Test
    void signalBroadcastCompletesAllWaitingWatchers() {
        // Start three back-order watchers
        ProcessInstance w1 = runtimeService.startProcessInstanceByKey(
            "stock-watcher", Map.of("orderId", "ORD-A"));
        ProcessInstance w2 = runtimeService.startProcessInstanceByKey(
            "stock-watcher", Map.of("orderId", "ORD-B"));
        ProcessInstance w3 = runtimeService.startProcessInstanceByKey(
            "stock-watcher", Map.of("orderId", "ORD-C"));

        // All three should be waiting at the signal catch event
        assertThat(runtimeService.createEventSubscriptionQuery()
            .eventType("signal")
            .eventName("StockAvailable")
            .count()).isEqualTo(3);

        // Start inventory-manager — it throws the signal during execution
        ProcessInstance manager = runtimeService.startProcessInstanceByKey(
            "inventory-manager", Map.of("productId", "PROD-X"));

        // Manager should complete
        assertCompleted(manager, "EndEvent_RestockComplete");

        // All watchers should also complete (signal broadcast reached all)
        assertCompleted(w1, "EndEvent_OrderFulfilled");
        assertCompleted(w2, "EndEvent_OrderFulfilled");
        assertCompleted(w3, "EndEvent_OrderFulfilled");
    }

    @Test
    void signalApiDirectlyWakesAllSubscribers() {
        ProcessInstance w1 = runtimeService.startProcessInstanceByKey(
            "stock-watcher", Map.of("orderId", "ORD-D"));
        ProcessInstance w2 = runtimeService.startProcessInstanceByKey(
            "stock-watcher", Map.of("orderId", "ORD-E"));

        // Use API to broadcast signal directly (without a throwing process)
        runtimeService.signalEventReceived("StockAvailable");

        assertCompleted(w1, "EndEvent_OrderFulfilled");
        assertCompleted(w2, "EndEvent_OrderFulfilled");
    }

    @Test
    void noSubscribersWhenSignalIsBroadcast() {
        // Verify count is 0 before starting any watchers
        long countBefore = runtimeService.createEventSubscriptionQuery()
            .eventType("signal").eventName("StockAvailable").count();

        // Broadcasting with no subscribers should not throw
        runtimeService.signalEventReceived("StockAvailable");

        // Count unchanged
        assertThat(runtimeService.createEventSubscriptionQuery()
            .eventType("signal").eventName("StockAvailable").count())
            .isEqualTo(countBefore);
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance h = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).singleResult();
        assertThat(h.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(h.getEndActivityId()).isEqualTo(endEventId);
    }
}
