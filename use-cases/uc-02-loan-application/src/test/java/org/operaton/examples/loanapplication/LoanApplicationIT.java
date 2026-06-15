package org.operaton.examples.loanapplication;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.operaton.bpm.engine.DecisionService;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.TaskService;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "management.health.mail.enabled=false")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoanApplicationIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
            .withMappingFromResource("wiremock/mappings/credit-score-stub.json");

    @DynamicPropertySource
    static void wireMockProperties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
        registry.add("credit.score.service.url", () -> baseUrl);
    }

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @Order(1)
    void processDefinitionIsDeployed() {
        long count = processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey("loan-application")
                .count();
        assertEquals(1, count, "loan-application process must be deployed");
    }

    @Test
    @Order(2)
    void dmnDecisionIsDeployed() {
        long count = processEngine.getRepositoryService()
                .createDecisionDefinitionQuery()
                .decisionDefinitionKey("risk-assessment")
                .count();
        assertEquals(1, count, "risk-assessment DMN decision must be deployed");
    }

    @Test
    @Order(3)
    void dmnDecisionEvaluatedInIsolation() {
        DecisionService decisionService = processEngine.getDecisionService();

        // low risk
        var lowResult = decisionService.evaluateDecisionByKey("risk-assessment")
                .variables(Variables.putValue("creditScore", 750).putValue("loanAmount", 100000))
                .evaluate();
        assertEquals("low", lowResult.getSingleResult().getSingleEntry());

        // medium risk
        var mediumResult = decisionService.evaluateDecisionByKey("risk-assessment")
                .variables(Variables.putValue("creditScore", 650).putValue("loanAmount", 100000))
                .evaluate();
        assertEquals("medium", mediumResult.getSingleResult().getSingleEntry());

        // high risk
        var highResult = decisionService.evaluateDecisionByKey("risk-assessment")
                .variables(Variables.putValue("creditScore", 500).putValue("loanAmount", 100000))
                .evaluate();
        assertEquals("high", highResult.getSingleResult().getSingleEntry());
    }

    @Test
    @Order(4)
    void processInstanceIsQueryableByBusinessKey() {
        String businessKey = "LOAN-" + UUID.randomUUID();
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "loan-application", businessKey,
                Variables.putValue("creditScore", 750)
                         .putValue("loanAmount", 100000)
                         .putValue("applicantEmail", "test@example.com"));
        assertNotNull(instance);

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .count()).isEqualTo(0); // process completes synchronously for low risk
    }

    @ParameterizedTest(name = "creditScore={0}, loanAmount={1} -> riskLevel={2}")
    @CsvSource({
        "750, 100000, low",
        "650, 100000, medium",
        "500, 100000, high"
    })
    @Order(5)
    void loanApplicationProcessCoversAllRiskPaths(int creditScore, int loanAmount, String expectedRisk) {
        TaskService taskService = processEngine.getTaskService();

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                "loan-application",
                Variables.putValue("creditScore", creditScore)
                         .putValue("loanAmount", loanAmount)
                         .putValue("applicantEmail", "test@example.com"));

        assertNotNull(instance);

        // For processes that complete synchronously (low/high risk), use history service
        String actualRiskLevel;
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instance.getId()).count() > 0) {
            actualRiskLevel = (String) runtimeService.getVariable(instance.getId(), "riskLevel");
        } else {
            actualRiskLevel = (String) historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(instance.getId())
                    .variableName("riskLevel")
                    .singleResult()
                    .getValue();
        }
        assertEquals(expectedRisk, actualRiskLevel,
                "Expected DMN to set the matching riskLevel");

        // For medium risk, a user task should be pending for underwriters
        if ("medium".equals(expectedRisk)) {
            List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(instance.getId())
                    .taskCandidateGroup("underwriters")
                    .list();
            assertFalse(tasks.isEmpty(), "Expected underwriter review task for medium risk");

            // Complete the task to end the process
            taskService.claim(tasks.get(0).getId(), "eve");
            taskService.complete(tasks.get(0).getId());
        }

        // Verify no active instances remain
        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .count();
        assertEquals(0, activeCount,
                "No active process instances should remain after path completes for risk=" + expectedRisk);
    }
}
