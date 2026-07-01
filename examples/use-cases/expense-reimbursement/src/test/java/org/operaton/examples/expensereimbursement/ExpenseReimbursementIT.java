package org.operaton.examples.expensereimbursement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = ExpenseReimbursementIT.Initializer.class)
class ExpenseReimbursementIT {

    // Fake image bytes — WireMock matches on requesterName in the text portion, not the image
    private static final byte[] FAKE_RECEIPT = "fake-receipt-image".getBytes(StandardCharsets.UTF_8);

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
        .withMappingFromResource("wiremock/mappings/llm-receipt-match.json")
        .withMappingFromResource("wiremock/mappings/llm-receipt-unrelated.json")
        .withMappingFromResource("wiremock/mappings/llm-receipt-match-overtier.json")
        .withMappingFromResource("wiremock/mappings/llm-email-approved.json")
        .withMappingFromResource("wiremock/mappings/llm-email-rejected.json");

    @Container
    @SuppressWarnings("rawtypes")
    static GenericContainer mailpit = new GenericContainer<>("axllent/mailpit:latest")
        .withExposedPorts(1025, 8025);

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            String wireMockBase = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
            TestPropertyValues.of(
                "llm.base-url=" + wireMockBase,
                "spring.mail.host=" + mailpit.getHost(),
                "spring.mail.port=" + mailpit.getMappedPort(1025)
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    private final RestTemplate rest = new RestTemplate();

    @BeforeEach
    void clearMailpit() {
        rest.delete("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages");
    }

    private ProcessInstance startExpense(String requesterName, String requesterEmail,
                                          String kind, double statedCost, String reason) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("requesterName", requesterName);
        vars.put("requesterEmail", requesterEmail);
        vars.put("kind", kind);
        vars.put("statedCost", statedCost);
        vars.put("reason", reason);
        vars.put("receipt", Variables.fileValue("receipt.jpg")
            .file(FAKE_RECEIPT)
            .mimeType("image/jpeg")
            .create());
        return runtimeService.startProcessInstanceByKey("expense-reimbursement", vars);
    }

    private HistoricProcessInstance historic(ProcessInstance pi) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(pi.getId()).singleResult();
    }

    private Object var(ProcessInstance pi, String name) {
        var v = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName(name).singleResult();
        return v != null ? v.getValue() : null;
    }

    private int mailCount() {
        Map<?, ?> r = rest.getForObject(
            "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages",
            Map.class);
        return r != null ? ((Number) r.get("total")).intValue() : 0;
    }

    @Test
    void happyPath_matchWithinTier_autoApproved() {
        // MEALS €35 < €50 threshold — no approval needed
        ProcessInstance pi = startExpense("Alice Berger", "alice@example.com", "MEALS", 35.0, "Team lunch");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HistoricProcessInstance h = historic(pi);
            assertThat(h).isNotNull();
            assertThat(h.getEndActivityId()).isEqualTo("EndEvent_Reimbursed");
        });

        assertThat(var(pi, "matchResult")).isEqualTo("MATCH");
        assertThat(var(pi, "approvalRequired")).isEqualTo(false);
        assertThat(var(pi, "paymentReference")).asString().startsWith("PAY-");
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(mailCount()).isEqualTo(1));
    }

    @Test
    void unrelatedReceipt_forcesApproval_thenApproved() {
        // UNRELATED → approvalRequired=true regardless of kind/cost; approver approves
        ProcessInstance pi = startExpense("Bob Richter", "bob@example.com", "TRAVEL", 150.0, "Conference travel");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey("UserTask_ApproveReimbursement")
                .singleResult();
            assertThat(task).as("Approval task must exist").isNotNull();
        });

        assertThat(var(pi, "matchResult")).isEqualTo("UNRELATED");
        assertThat(var(pi, "approvalRequired")).isEqualTo(true);

        Task task = taskService.createTaskQuery()
            .processInstanceId(pi.getId())
            .taskDefinitionKey("UserTask_ApproveReimbursement")
            .singleResult();
        taskService.complete(task.getId(), Map.of("approved", true));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            HistoricProcessInstance h = historic(pi);
            assertThat(h).isNotNull();
            assertThat(h.getEndActivityId()).isEqualTo("EndEvent_Reimbursed");
        });

        assertThat(var(pi, "paymentReference")).asString().startsWith("PAY-");
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(mailCount()).isEqualTo(1));
    }

    @Test
    void matchButOverTier_approvalRequired_thenRejected() {
        // EQUIPMENT €1200 > €1000 threshold → approvalRequired=true; approver rejects
        ProcessInstance pi = startExpense("Charlie Weiss", "charlie@example.com", "EQUIPMENT", 1200.0, "Laptop purchase");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey("UserTask_ApproveReimbursement")
                .singleResult();
            assertThat(task).as("Approval task must exist").isNotNull();
        });

        assertThat(var(pi, "matchResult")).isEqualTo("MATCH");
        assertThat(var(pi, "approvalRequired")).isEqualTo(true);

        Task task = taskService.createTaskQuery()
            .processInstanceId(pi.getId())
            .taskDefinitionKey("UserTask_ApproveReimbursement")
            .singleResult();
        taskService.complete(task.getId(), Map.of("approved", false));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            HistoricProcessInstance h = historic(pi);
            assertThat(h).isNotNull();
            assertThat(h.getEndActivityId()).isEqualTo("EndEvent_Rejected");
        });

        assertThat(var(pi, "paymentReference")).isNull();
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(mailCount()).isEqualTo(1));
    }
}
