package org.operaton.examples.contractsigning;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.*;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContractSigningIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static GenericContainer<?> rustfs = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:latest"))
        .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
        .withEnv("RUSTFS_SECRET_KEY", "minioadmin")
        .withExposedPorts(9000)
        .withStartupTimeout(java.time.Duration.ofSeconds(60));

    @Autowired private ProcessEngine processEngine;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private HistoryService historyService;
    @Autowired private org.springframework.boot.web.server.WebServer webServer;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost:" + webServer.getPort();
    }

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        String rustfsUrl = "http://" + rustfs.getHost() + ":" + rustfs.getFirstMappedPort();
        registry.add("storage.endpoint", () -> rustfsUrl);
    }

    @Test
    void processDefinitionIsDeployed() {
        assertThat(processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("contract-signing")
            .count()).isEqualTo(1);
    }

    @Test
    void happyPath_bothPartiesSign_completesWithArchivedArtifact() throws IOException {
        // Create/upload sample PDF
        Response response = RestAssured
            .given()
                .multiPart("file", new java.io.File("src/test/resources/sample-contract.pdf"))
                .multiPart("customer", "Alice")
                .multiPart("company", "Operaton GmbH")
            .when()
                .post("/contracts")
            .then()
                .statusCode(201)
                .extract().response();

        ContractUploadResponse uploadResponse = response.as(ContractUploadResponse.class);
        String processInstanceId = uploadResponse.processInstanceId;
        assertThat(processInstanceId).isNotBlank();

        // Customer signs
        List<Task> customerTasks = taskService.createTaskQuery()
            .taskCandidateGroup("customers")
            .processInstanceId(processInstanceId)
            .list();
        assertThat(customerTasks).hasSize(1);
        Task customerTask = customerTasks.get(0);
        taskService.claim(customerTask.getId(), "alice");
        taskService.setVariable(customerTask.getId(), "signDecision", "signed");
        taskService.complete(customerTask.getId());

        // Company signs
        List<Task> companyTasks = taskService.createTaskQuery()
            .taskCandidateGroup("legal")
            .processInstanceId(processInstanceId)
            .list();
        assertThat(companyTasks).hasSize(1);
        Task companyTask = companyTasks.get(0);
        taskService.claim(companyTask.getId(), "bob");
        taskService.setVariable(companyTask.getId(), "signDecision", "signed");
        taskService.complete(companyTask.getId());

        // Wait for completion
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ContractExecuted");
        });

        // Anti-pattern guard: no byte[] variables
        var variables = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .list();
        for (var var : variables) {
            assertThat(var.getTypeName())
                .as("Variable %s must not be byte[]", var.getName())
                .isNotEqualTo("Files");
        }

        // Assert finalKey and documentHash set
        Object finalKey = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName("finalKey")
            .singleResult().getValue();
        assertThat(finalKey).isNotNull().isInstanceOf(String.class);

        Object documentHash = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName("documentHash")
            .singleResult().getValue();
        assertThat(documentHash).isNotNull();
    }

    @Test
    void customerDeclines_terminatesInstance_noCompanySigning() throws IOException {
        Response response = RestAssured
            .given()
                .multiPart("file", new java.io.File("src/test/resources/sample-contract.pdf"))
                .multiPart("customer", "Bob")
                .multiPart("company", "Operaton GmbH")
            .when()
                .post("/contracts")
            .then()
                .statusCode(201)
                .extract().response();

        ContractUploadResponse uploadResponse = response.as(ContractUploadResponse.class);
        String processInstanceId = uploadResponse.processInstanceId;

        // Customer declines
        Task customerTask = taskService.createTaskQuery()
            .taskCandidateGroup("customers")
            .processInstanceId(processInstanceId)
            .singleResult();
        taskService.claim(customerTask.getId(), "alice");
        taskService.setVariable(customerTask.getId(), "signDecision", "declined");
        taskService.complete(customerTask.getId());

        // Wait for termination
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_DeclinedByCustomer");
        });

        // Company task should not exist
        long companyTaskCount = taskService.createTaskQuery()
            .taskCandidateGroup("legal")
            .processInstanceId(processInstanceId)
            .count();
        assertThat(companyTaskCount).isZero();
    }

    static class ContractUploadResponse {
        public String processInstanceId;
        public String businessKey;
        public String draftKey;

        public String getProcessInstanceId() { return processInstanceId; }
        public void setProcessInstanceId(String id) { this.processInstanceId = id; }
        public String getBusinessKey() { return businessKey; }
        public void setBusinessKey(String key) { this.businessKey = key; }
        public String getDraftKey() { return draftKey; }
        public void setDraftKey(String key) { this.draftKey = key; }
    }
}
