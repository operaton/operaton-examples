package org.operaton.examples.bankaccountopening;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "operaton.worker.enabled=false"
)
@Testcontainers
@ContextConfiguration(initializers = BankAccountOpeningIT.Initializer.class)
class BankAccountOpeningIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
        .withMappingFromResource("wiremock/mappings/llm-risk-low.json")
        .withMappingFromResource("wiremock/mappings/llm-risk-high.json");

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
    private ExternalTaskClient taskClient;

    // Injected from SpringBootTest so we can build the external-task client URL
    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    @BeforeEach
    void clearMailAndStartWorker() {
        // Clear Mailpit inbox
        rest.delete("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages");

        // Inline identity worker replacing the production IdentityValidationWorker
        taskClient = ExternalTaskClient.create()
            .baseUrl("http://localhost:" + port + "/engine-rest")
            .lockDuration(5_000)
            .build();

        taskClient.subscribe("identity-validation")
            .handler((task, service) -> {
                if (Boolean.TRUE.equals(task.getVariable("simulateIdentityFail"))) {
                    service.complete(task, Map.of("identityVerified", false, "identityScore", 0));
                } else {
                    service.complete(task, Map.of("identityVerified", true, "identityScore", 82));
                }
            })
            .open();
    }

    @AfterEach
    void stopWorker() {
        if (taskClient != null) {
            taskClient.stop();
        }
    }

    /**
     * Build a baseline set of process variables, merging any extra vars on top.
     */
    private ProcessInstance startApplication(Map<String, Object> extraVars) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", "Anna Berger");
        vars.put("email", "anna.berger@example.com");
        vars.put("dateOfBirth", "1985-03-15");
        vars.put("gender", "F");
        vars.put("nationality", "DE");
        vars.put("countryOfResidence", "DE");
        vars.put("annualIncome", 55000L);
        vars.put("employmentStatus", "EMPLOYED");
        vars.put("occupation", "Software Engineer");
        vars.put("sourceOfFunds", "SALARY");
        vars.put("idDocumentType", "PASSPORT");
        vars.put("idDocumentNumber", "AB123456");
        vars.put("requestedAccountType", "CHECKING");
        vars.putAll(extraVars);
        return runtimeService.startProcessInstanceByKey("bank-account-opening", vars);
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
    void happyPath_approveApplication() {
        // nationality=DE → WireMock returns LOW risk; inline worker returns verified=true, score=82
        // DMN: true + 82 + LOW → APPROVE
        ProcessInstance pi = startApplication(Map.of());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HistoricProcessInstance h = historic(pi);
            assertThat(h).isNotNull();
            assertThat(h.getEndActivityId()).isEqualTo("EndEvent_AccountOpened");
        });

        assertThat(var(pi, "decision")).isEqualTo("APPROVE");
        assertThat(var(pi, "iban")).asString().startsWith("DE");
        assertThat(var(pi, "status")).isEqualTo("OPENED");
        assertThat(var(pi, "backgroundRisk")).isEqualTo("LOW");
        assertThat(var(pi, "identityVerified")).isEqualTo(true);
        assertThat(var(pi, "identityScore")).isNotNull();

        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(mailCount()).isEqualTo(1));
    }

    @Test
    void alternativePath_rejectApplication() {
        // simulateIdentityFail=true → identityVerified=false → DMN: false → REJECT
        ProcessInstance pi = startApplication(Map.of("simulateIdentityFail", true));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HistoricProcessInstance h = historic(pi);
            assertThat(h).isNotNull();
            assertThat(h.getEndActivityId()).isEqualTo("EndEvent_ApplicationRejected");
        });

        assertThat(var(pi, "decision")).isEqualTo("REJECT");
        assertThat(var(pi, "iban")).isNull();

        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(mailCount()).isEqualTo(1));
    }

    @Test
    void manualReview_thenApprove() {
        // nationality=SY → WireMock returns HIGH risk; inline worker returns verified=true, score=82
        // DMN: true + 82 + HIGH → MANUAL_REVIEW
        ProcessInstance pi = startApplication(Map.of(
            "nationality", "SY",
            "countryOfResidence", "SY"
        ));

        // Wait for compliance review user task to appear
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Task task = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey("UserTask_ComplianceReview")
                .singleResult();
            assertThat(task).as("Compliance review task must exist").isNotNull();
        });

        Task task = taskService.createTaskQuery()
            .processInstanceId(pi.getId())
            .taskDefinitionKey("UserTask_ComplianceReview")
            .singleResult();

        assertThat(var(pi, "decision")).isEqualTo("MANUAL_REVIEW");
        assertThat(var(pi, "backgroundRisk")).isEqualTo("HIGH");

        // Compliance officer approves
        taskService.complete(task.getId(), Map.of("reviewOutcome", "APPROVE"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            HistoricProcessInstance h = historic(pi);
            assertThat(h).isNotNull();
            assertThat(h.getEndActivityId()).isEqualTo("EndEvent_AccountOpened");
        });

        assertThat(var(pi, "iban")).asString().startsWith("DE");

        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(mailCount()).isEqualTo(1));
    }

    @Test
    void parallelJoin_bothVariablesSet() {
        // Both identityVerified AND backgroundRisk must be set before DMN runs (proves parallel join)
        ProcessInstance pi = startApplication(Map.of());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(var(pi, "identityVerified")).isNotNull();
            assertThat(var(pi, "backgroundRisk")).isNotNull();
        });

        // After join, decision must also be set (DMN ran after both branches joined)
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(var(pi, "decision")).isNotNull());
    }
}
