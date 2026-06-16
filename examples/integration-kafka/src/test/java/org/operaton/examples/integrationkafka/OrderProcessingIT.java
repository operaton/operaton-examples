package org.operaton.examples.integrationkafka;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class OrderProcessingIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics.orders}")
    String ordersTopic;

    @Test
    void orderMessageStartsProcessAndPublishesResult() throws Exception {
        String orderId = "ORDER-TC-001";

        kafkaTemplate.send(ordersTopic, orderId, orderId).get(5, TimeUnit.SECONDS);

        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> {
                long completed = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey(orderId)
                    .completed()
                    .count();
                assertThat(completed).isEqualTo(1);
            });

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
