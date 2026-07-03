package org.operaton.examples.integrationrest;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = ProductAvailabilityProcessIT.WireMockInitializer.class)
class ProductAvailabilityProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
            .withMappingFromResource("wiremock/mappings/inventory-available.json");

    static class WireMockInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "inventory.service.url=http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080)
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void inStockProductConfirmsOrder() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "product-availability",
            Map.of("productId", "PROD-001", "quantity", 2));

        assertCompleted(instance, "EndEvent_Confirmed");
        assertThat(getHistoricVariable(instance.getId(), "inventoryAvailable")).isEqualTo(true);
    }

    @Test
    void outOfStockProductEndsAsOutOfStock() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "product-availability",
            Map.of("productId", "PROD-EMPTY", "quantity", 1));

        assertCompleted(instance, "EndEvent_OutOfStock");
        assertThat(getHistoricVariable(instance.getId(), "inventoryAvailable")).isEqualTo(false);
    }

    @Test
    void notFoundProductTriggersBpmnError() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "product-availability",
            Map.of("productId", "PROD-UNKNOWN", "quantity", 1));

        assertCompleted(instance, "EndEvent_Error");
    }

    private void assertCompleted(ProcessInstance instance, String endEventId) {
        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo(endEventId);
    }

    private Object getHistoricVariable(String processInstanceId, String name) {
        var v = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName(name)
            .singleResult();
        return v != null ? v.getValue() : null;
    }
}
