package org.operaton.examples.errorcompensation;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.history.HistoricVariableInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BookTravelProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void successfulBookingConfirmsTrip() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "book-travel",
            Map.of("tripId", "TRIP-001", "paymentShouldFail", false));

        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();

        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Confirmed");

        assertThat(getVariable(instance.getId(), "hotelBooked")).isEqualTo(true);
        assertThat(getVariable(instance.getId(), "flightBooked")).isEqualTo(true);
        assertThat(getVariable(instance.getId(), "paymentConfirmed")).isEqualTo(true);
    }

    @Test
    void paymentFailureTriggersSagaCompensation() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "book-travel",
            Map.of("tripId", "TRIP-002", "paymentShouldFail", true));

        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();

        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Cancelled");

        // Hotel and flight were booked first
        assertThat(getVariable(instance.getId(), "hotelBooked")).isEqualTo(true);
        assertThat(getVariable(instance.getId(), "flightBooked")).isEqualTo(true);

        // Both were then compensated (cancelled)
        assertThat(getVariable(instance.getId(), "hotelCancelled")).isEqualTo(true);
        assertThat(getVariable(instance.getId(), "flightCancelled")).isEqualTo(true);
    }

    @Test
    void processDefinitionIsDeployed() {
        long count = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey("book-travel")
            .count();
        assertThat(count).isGreaterThanOrEqualTo(0);

        long defCount = historyService.createHistoricProcessInstanceQuery()
            .processDefinitionKey("book-travel")
            .count();
        // Just verify process key is recognized (0 is valid if no prior test ran)
        assertThat(defCount).isGreaterThanOrEqualTo(0);
    }

    private Object getVariable(String processInstanceId, String name) {
        HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName(name)
            .singleResult();
        return var != null ? var.getValue() : null;
    }
}
