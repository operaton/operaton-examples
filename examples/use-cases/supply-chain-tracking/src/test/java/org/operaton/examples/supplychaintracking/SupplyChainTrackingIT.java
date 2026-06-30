package org.operaton.examples.supplychaintracking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.impl.util.ClockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SupplyChainTrackingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine"); // NOSONAR

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @LocalServerPort int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kafka.topics.package-events}")
    String packageEventsTopic;

    @Value("${kafka.topics.customs-events}")
    String customsEventsTopic;

    @AfterEach
    void resetClock() {
        ClockUtil.reset();
    }

    @Test
    void happyPath_packageDelivered_completesAtEndDelivered() throws Exception {
        // Dispatch shipment via REST
        var req = new ShipmentController.ShipmentRequest("ORD-001", "TRK-HP-001", "Berlin");
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/shipments", req, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Wait for process to be parked at gateway (active instance exists)
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-HP-001").count()).isEqualTo(1)
        );

        // Publish PackageDelivered event
        String payload = "{\"trackingNumber\":\"TRK-HP-001\"}";
        kafkaTemplate.send(packageEventsTopic, "TRK-HP-001", payload).get(5, TimeUnit.SECONDS);

        // Await process completion
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long completed = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-HP-001").completed().count();
            assertThat(completed).isEqualTo(1);
        });

        var historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey("TRK-HP-001").singleResult();
        assertThat(historic.getEndActivityId()).isEqualTo("EndDelivered");

        var statusVar = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(historic.getId()).variableName("status").singleResult();
        assertThat(statusVar.getValue()).isEqualTo("DELIVERED");
    }

    @Test
    void customsDelayThenDelivery_completesAtEndDelivered() throws Exception {
        var req = new ShipmentController.ShipmentRequest("ORD-002", "TRK-CD-001", "Paris");
        restTemplate.postForEntity("http://localhost:" + port + "/shipments", req, Map.class);

        // Wait for process to park at gateway
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-CD-001").count()).isEqualTo(1)
        );

        // Publish CustomsDelay
        String delayPayload = "{\"trackingNumber\":\"TRK-CD-001\",\"eta\":\"2026-07-15\"}";
        kafkaTemplate.send(customsEventsTopic, "TRK-CD-001", delayPayload).get(5, TimeUnit.SECONDS);

        // Give the listener time to correlate and re-enter gateway
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var vars = runtimeService.createVariableInstanceQuery()
                .processInstanceIdIn(
                    runtimeService.createProcessInstanceQuery()
                        .processInstanceBusinessKey("TRK-CD-001").singleResult().getId()
                ).variableName("delayCount").list();
            assertThat(vars).isNotEmpty();
            assertThat(vars.get(0).getValue()).isEqualTo(1);
        });

        // Now deliver
        kafkaTemplate.send(packageEventsTopic, "TRK-CD-001", "{\"trackingNumber\":\"TRK-CD-001\"}")
            .get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long completed = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-CD-001").completed().count();
            assertThat(completed).isEqualTo(1);
        });

        var historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey("TRK-CD-001").singleResult();
        assertThat(historic.getEndActivityId()).isEqualTo("EndDelivered");
    }

    @Test
    void timerPath_noEventIn7Days_completesAtFollowupRequired() throws Exception {
        var req = new ShipmentController.ShipmentRequest("ORD-003", "TRK-TM-001", "Tokyo");
        restTemplate.postForEntity("http://localhost:" + port + "/shipments", req, Map.class);

        // Wait for process to park at gateway
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-TM-001").count()).isEqualTo(1)
        );

        // Advance clock 8 days so the P7D timer is due
        ClockUtil.setCurrentTime(new Date(ClockUtil.getCurrentTime().getTime() + Duration.ofDays(8).toMillis()));

        // Job executor picks up the due timer — await completion
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long completed = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-TM-001").completed().count();
            assertThat(completed).isEqualTo(1);
        });

        var historic = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey("TRK-TM-001").singleResult();
        assertThat(historic.getEndActivityId()).isEqualTo("EndFollowup");

        var statusVar = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(historic.getId()).variableName("status").singleResult();
        assertThat(statusVar.getValue()).isEqualTo("FOLLOW_UP_REQUIRED");
    }

    @Test
    void businessKeyIsolation_twoShipments_correlateIndependently() throws Exception {
        restTemplate.postForEntity("http://localhost:" + port + "/shipments",
            new ShipmentController.ShipmentRequest("ORD-A", "TRK-ISO-A", "London"), Map.class);
        restTemplate.postForEntity("http://localhost:" + port + "/shipments",
            new ShipmentController.ShipmentRequest("ORD-B", "TRK-ISO-B", "Rome"), Map.class);

        // Both active
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-ISO-A").count()).isEqualTo(1);
            assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-ISO-B").count()).isEqualTo(1);
        });

        // Deliver only A
        kafkaTemplate.send(packageEventsTopic, "TRK-ISO-A", "{\"trackingNumber\":\"TRK-ISO-A\"}")
            .get(5, TimeUnit.SECONDS);

        // A completes, B still active
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long completedA = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-ISO-A").completed().count();
            assertThat(completedA).isEqualTo(1);
            assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey("TRK-ISO-B").count()).isEqualTo(1);
        });
    }
}
