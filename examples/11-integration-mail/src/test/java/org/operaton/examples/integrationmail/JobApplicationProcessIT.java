package org.operaton.examples.integrationmail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = JobApplicationProcessIT.MailpitInitializer.class)
class JobApplicationProcessIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @SuppressWarnings("rawtypes")
    static GenericContainer mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025);

    static class MailpitInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "spring.mail.host=" + mailpit.getHost(),
                "spring.mail.port=" + mailpit.getMappedPort(1025)
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    private RestTemplate restTemplate = new RestTemplate();

    @BeforeEach
    void clearMailpit() {
        String deleteUrl = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages";
        restTemplate.delete(deleteUrl);
    }

    @Test
    void submittingApplicationSendsConfirmationEmail() {
        runtimeService.startProcessInstanceByKey(
            "job-application",
            Map.of(
                "applicantName", "Jane Doe",
                "applicantEmail", "jane@example.com"
            ));

        // Confirmation email should be sent immediately (before user task)
        String messages = getMailpitMessages();
        assertThat(messages).contains("jane@example.com");
        assertThat(messages).contains("Application Received");
    }

    @Test
    void approvingApplicationSendsApprovalEmail() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "job-application",
            Map.of(
                "applicantName", "John Smith",
                "applicantEmail", "john@example.com"
            ));

        Task reviewTask = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(reviewTask).isNotNull();

        taskService.claim(reviewTask.getId(), "hr-manager");
        taskService.complete(reviewTask.getId(), Map.of("approved", true));

        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);

        String messages = getMailpitMessages();
        assertThat(messages).contains("Application Approved");
    }

    @Test
    void rejectingApplicationSendsRejectionEmail() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "job-application",
            Map.of(
                "applicantName", "Alice Brown",
                "applicantEmail", "alice@example.com"
            ));

        Task reviewTask = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .singleResult();
        taskService.claim(reviewTask.getId(), "hr-manager");
        taskService.complete(reviewTask.getId(), Map.of("approved", false));

        String messages = getMailpitMessages();
        assertThat(messages).contains("Application Update");
    }

    private String getMailpitMessages() {
        String url = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages";
        return restTemplate.getForObject(url, String.class);
    }
}
