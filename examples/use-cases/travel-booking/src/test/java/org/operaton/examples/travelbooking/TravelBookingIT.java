package org.operaton.examples.travelbooking;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TravelBookingIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void processDefinitionIsDeployed() {
        assertThat(processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("travel-booking")
            .count()).isEqualTo(1);
    }

    @Test
    void happyPath_withinBudget_booksTrip() {
        // budget 2000, total 1200 (800+300+100) — within budget
        var pi = runtimeService.startProcessInstanceByKey(
            "travel-booking",
            "TRIP-001",
            Variables.createVariables()
                .putValue("tripId", "TRIP-001")
                .putValue("customer", "Alice")
                .putValue("destination", "Paris")
                .putValue("budget", 2000.0)
                .putValue("flightPrice", 800.0)
                .putValue("hotelPrice", 300.0)
                .putValue("carPrice", 100.0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_TripBooked");
        });

        var vars = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId())
            .list();

        assertThat(vars).extracting("name").contains("flightRef", "hotelRef", "carRef");

        var paymentApproved = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("paymentApproved").singleResult();
        assertThat((Boolean) paymentApproved.getValue()).isTrue();

        // Compensation handlers should NOT have run — no *Cancelled variables
        var flightCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("flightCancelled").singleResult();
        assertThat(flightCancelled).isNull();
    }

    @Test
    void cancelPath_overBudget_cancelsAndCompensates() {
        // budget 1000, total 1200 (800+300+100) — over budget
        var pi = runtimeService.startProcessInstanceByKey(
            "travel-booking",
            "TRIP-002",
            Variables.createVariables()
                .putValue("tripId", "TRIP-002")
                .putValue("customer", "Bob")
                .putValue("destination", "Tokyo")
                .putValue("budget", 1000.0)
                .putValue("flightPrice", 800.0)
                .putValue("hotelPrice", 300.0)
                .putValue("carPrice", 100.0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_TripCancelled");
        });

        // paymentApproved == false
        var paymentApproved = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("paymentApproved").singleResult();
        assertThat((Boolean) paymentApproved.getValue()).isFalse();

        // All three compensation handlers must have run
        var flightCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("flightCancelled").singleResult();
        assertThat(flightCancelled).isNotNull();
        assertThat((Boolean) flightCancelled.getValue()).isTrue();

        var hotelCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("hotelCancelled").singleResult();
        assertThat(hotelCancelled).isNotNull();
        assertThat((Boolean) hotelCancelled.getValue()).isTrue();

        var carCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("carCancelled").singleResult();
        assertThat(carCancelled).isNotNull();
        assertThat((Boolean) carCancelled.getValue()).isTrue();
    }
}
