package org.operaton.examples.messageevents;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.MismatchingMessageCorrelationException;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class OrderShipmentProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void orderPlacedAndShipmentReadyCompletesDelivery() {
        ProcessInstance instance = runtimeService.startProcessInstanceByMessage(
            "OrderPlaced",
            "ORDER-001",
            Map.of("orderId", "ORDER-001", "customerId", "CUST-42")
        );
        assertThat(instance).isNotNull();
        assertThat(instance.getBusinessKey()).isEqualTo("ORDER-001");

        assertThat(runtimeService.createEventSubscriptionQuery()
            .processInstanceId(instance.getId())
            .eventName("ShipmentReady")
            .count()).isEqualTo(1);

        runtimeService.correlateMessage("ShipmentReady", "ORDER-001",
            Map.of("trackingId", "TRACK-XYZ"));

        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Delivered");

        assertThat(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName("trackingId")
            .singleResult()
            .getValue()).isEqualTo("TRACK-XYZ");
    }

    @Test
    void correlatingToNonExistentInstanceThrows() {
        assertThatThrownBy(() ->
            runtimeService.correlateMessage("ShipmentReady", "ORDER-UNKNOWN")
        ).isInstanceOf(MismatchingMessageCorrelationException.class);
    }

    @Test
    void multipleOrdersAreCorrelatedIndependently() {
        ProcessInstance order1 = runtimeService.startProcessInstanceByMessage(
            "OrderPlaced", "ORDER-A01", Map.of("orderId", "ORDER-A01"));
        ProcessInstance order2 = runtimeService.startProcessInstanceByMessage(
            "OrderPlaced", "ORDER-A02", Map.of("orderId", "ORDER-A02"));

        runtimeService.correlateMessage("ShipmentReady", "ORDER-A02",
            Map.of("trackingId", "TRACK-B"));
        runtimeService.correlateMessage("ShipmentReady", "ORDER-A01",
            Map.of("trackingId", "TRACK-A"));

        assertCompleted(order1, "EndEvent_Delivered");
        assertCompleted(order2, "EndEvent_Delivered");
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo(endEventId);
    }
}
