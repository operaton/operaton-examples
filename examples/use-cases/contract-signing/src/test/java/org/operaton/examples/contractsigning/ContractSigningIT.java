package org.operaton.examples.contractsigning;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.*;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.variable.VariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContractSigningIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> rustfs = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:latest"))
        .withEnv("RUSTFS_ACCESS_KEY", "minioadmin")
        .withEnv("RUSTFS_SECRET_KEY", "minioadmin")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/health/live").forPort(9000));

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ProcessEngine processEngine;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private HistoryService historyService;

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
        byte[] pdfBytes = Files.readAllBytes(Paths.get("src/test/resources/sample-contract.pdf"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource("src/test/resources/sample-contract.pdf"));
        body.add("customer", "Alice");
        body.add("company", "Operaton GmbH");

        ResponseEntity<ContractUploadResponse> response = restTemplate.postForEntity(
            "/contracts", new HttpEntity<>(body, headers), ContractUploadResponse.class);

        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        String processInstanceId = response.getBody().getProcessInstanceId();

        // Customer signs
        List<Task> customerTasks = taskService.createTaskQuery()
            .candidateGroup("customers")
            .processInstanceId(processInstanceId)
            .list();
        assertThat(customerTasks).hasSize(1);
        Task customerTask = customerTasks.get(0);
        taskService.claim(customerTask.getId(), "alice");
        taskService.setVariable(customerTask.getId(), "signDecision", "signed");
        taskService.complete(customerTask.getId());

        // Company signs
        List<Task> companyTasks = taskService.createTaskQuery()
            .candidateGroup("legal")
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
        List<VariableInstance> variables = runtimeService.createVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .list();
        for (VariableInstance var : variables) {
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
        byte[] pdfBytes = Files.readAllBytes(Paths.get("src/test/resources/sample-contract.pdf"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource("src/test/resources/sample-contract.pdf"));
        body.add("customer", "Bob");
        body.add("company", "Operaton GmbH");

        ResponseEntity<ContractUploadResponse> response = restTemplate.postForEntity(
            "/contracts", new HttpEntity<>(body, headers), ContractUploadResponse.class);

        String processInstanceId = response.getBody().getProcessInstanceId();

        // Customer declines
        Task customerTask = taskService.createTaskQuery()
            .candidateGroup("customers")
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
            .candidateGroup("legal")
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
