package org.operaton.examples.integrationconnectors;

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
@ContextConfiguration(initializers = CurrencyConversionIT.WireMockInitializer.class)
class CurrencyConversionIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
            .withMappingFromResource("wiremock/mappings/rate-success.json")
            .withMappingFromResource("wiremock/mappings/rate-not-found.json");

    static class WireMockInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "exchange-rate.service.url=http://" + wireMock.getHost()
                    + ":" + wireMock.getMappedPort(8080)
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void successfulConversionComputesAmount() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "currency-conversion",
            Map.of(
                "exchangeRateServiceUrl", wireMockUrl() + "/rates/USD/EUR",
                "amount", 100.0
            ));

        HistoricProcessInstance historic = historic(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Converted");

        Object converted = historicVar(instance, "convertedAmount");
        assertThat((Double) converted).isEqualTo(92.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void unavailableRateEndsAtErrorEvent() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "currency-conversion",
            Map.of(
                "exchangeRateServiceUrl", wireMockUrl() + "/rates/UNKNOWN/EUR",
                "amount", 50.0
            ));

        HistoricProcessInstance historic = historic(instance);
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_RateUnavailable");
    }

    private String wireMockUrl() {
        return "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
    }

    private HistoricProcessInstance historic(ProcessInstance instance) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
    }

    private Object historicVar(ProcessInstance instance, String name) {
        var v = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName(name)
            .singleResult();
        return v != null ? v.getValue() : null;
    }
}
