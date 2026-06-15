package org.operaton.examples.integrationkafka;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = OrderProcessingIT.KafkaInitializer.class)
class OrderProcessingIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    static class KafkaInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers()
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics.orders}")
    String ordersTopic;

    @Test
    void orderMessageStartsProcessAndPublishesResult() throws Exception {
        String orderId = "ORDER-TC-001";

        // Publish order to Kafka
        kafkaTemplate.send(ordersTopic, orderId, orderId).get(5, TimeUnit.SECONDS);

        // Wait for process to be started and completed by the listener
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> {
                long completed = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey(orderId)
                    .completed()
                    .count();
                assertThat(completed).isEqualTo(1);
            });

        // Verify the process instance was created with correct variables
        var historicInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(orderId)
            .singleResult();
        assertThat(historicInstance).isNotNull();
        var historicVars = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(historicInstance.getId())
            .variableName("orderId")
            .singleResult();
        assertThat(historicVars).isNotNull();
        assertThat(historicVars.getValue()).isEqualTo(orderId);
    }

    @Test
    void multipleOrdersAreProcessedIndependently() throws Exception {
        kafkaTemplate.send(ordersTopic, "ORDER-A", "ORDER-A").get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(ordersTopic, "ORDER-B", "ORDER-B").get(5, TimeUnit.SECONDS);

        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> {
                long completedA = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey("ORDER-A").completed().count();
                long completedB = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey("ORDER-B").completed().count();
                assertThat(completedA).isEqualTo(1);
                assertThat(completedB).isEqualTo(1);
            });
    }
}
