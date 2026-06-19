package org.operaton.examples.candidatescreening;

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
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = CandidateScreeningIT.WireMockInitializer.class)
class CandidateScreeningIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:3.5.4")
        .withMappingFromResource("wiremock/mappings/score-strong.json")
        .withMappingFromResource("wiremock/mappings/score-borderline.json")
        .withMappingFromResource("wiremock/mappings/score-weak.json")
        .withMappingFromResource("wiremock/mappings/email-invitation.json")
        .withMappingFromResource("wiremock/mappings/email-rejection.json")
        .withMappingFromResource("wiremock/mappings/email-summary.json")
        .withMappingFromResource("wiremock/mappings/calendar-freebusy.json");

    @Container
    @SuppressWarnings("rawtypes")
    static GenericContainer mailpit = new GenericContainer<>("axllent/mailpit:latest")
        .withExposedPorts(1025, 8025);

    static class WireMockInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            String base = "http://" + wireMock.getHost() + ":" + wireMock.getMappedPort(8080);
            TestPropertyValues.of(
                "llm.base-url=" + base,
                "calendar.base-url=" + base,
                "spring.mail.host=" + mailpit.getHost(),
                "spring.mail.port=" + mailpit.getMappedPort(1025)
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired HistoryService historyService;

    private final RestTemplate restTemplate = new RestTemplate();

    @BeforeEach
    void clearMailpit() {
        String url = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages";
        restTemplate.delete(url);
    }

    private int mailpitMessageCount() {
        String url = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages";
        Map<?, ?> response = restTemplate.getForObject(url, Map.class);
        return (Integer) response.get("total");
    }

    private ProcessInstance start(String name, String applicationText) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("candidateName", name);
        vars.put("position", "Senior Java Engineer");
        vars.put("applicationText", applicationText);
        vars.put("recruiterEmail", "rachel@example.com");
        vars.put("candidateEmail", "candidate@example.com");
        return runtimeService.startProcessInstanceByKey("candidate-screening", vars);
    }

    private HistoricProcessInstance historic(ProcessInstance instance) {
        return historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).singleResult();
    }

    private Object var(ProcessInstance instance, String name) {
        var v = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId()).variableName(name).singleResult();
        return v != null ? v.getValue() : null;
    }

    @Test
    void strongCandidateIsAutoInvited() {
        ProcessInstance instance = start("Ada Lindqvist", "Ten years of Java and Spring Boot leadership.");
        assertThat(historic(instance).getEndActivityId()).isEqualTo("EndEvent_Invited");
        assertThat(var(instance, "fitScore")).isEqualTo(88);
        assertThat(var(instance, "interviewSlot")).isEqualTo("2026-06-22T10:00:00");
        assertThat((String) var(instance, "invitationEmail")).isNotBlank();
        assertThat((String) var(instance, "recruiterSummaryEmail")).isNotBlank();
        await().untilAsserted(() -> assertThat(mailpitMessageCount()).isEqualTo(2)); // invitation + recruiter summary
    }

    @Test
    void weakCandidateIsRejected() {
        ProcessInstance instance = start("Wes Park", "Recent graduate, no Java experience.");
        assertThat(historic(instance).getEndActivityId()).isEqualTo("EndEvent_Rejected");
        assertThat(var(instance, "fitScore")).isEqualTo(35);
        assertThat((String) var(instance, "rejectionEmail")).isNotBlank();
        assertThat(var(instance, "interviewSlot")).isNull();
        await().untilAsserted(() -> assertThat(mailpitMessageCount()).isEqualTo(1)); // rejection only
    }

    @Test
    void borderlineApprovedIsInvited() {
        ProcessInstance instance = start("Bea Romano", "Mid-level developer with some Spring exposure.");
        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskDefinitionKey("UserTask_RecruiterReview").singleResult();
        assertThat(task).as("recruiter review task must exist").isNotNull();
        assertThat(var(instance, "assessment")).isNotNull();
        taskService.complete(task.getId(), Map.of("approved", true));
        assertThat(historic(instance).getEndActivityId()).isEqualTo("EndEvent_Invited");
        assertThat((String) var(instance, "invitationEmail")).isNotBlank();
        assertThat(var(instance, "recruiterSummaryEmail")).isNull();
        await().untilAsserted(() -> assertThat(mailpitMessageCount()).isEqualTo(1)); // invitation only, no recruiter summary
    }

    @Test
    void borderlineDeclinedIsRejected() {
        ProcessInstance instance = start("Bea Romano", "Mid-level developer with some Spring exposure.");
        Task task = taskService.createTaskQuery()
            .processInstanceId(instance.getId())
            .taskDefinitionKey("UserTask_RecruiterReview").singleResult();
        assertThat(task).isNotNull();
        taskService.complete(task.getId(), Map.of("approved", false));
        assertThat(historic(instance).getEndActivityId()).isEqualTo("EndEvent_Rejected");
        assertThat((String) var(instance, "rejectionEmail")).isNotBlank();
        await().untilAsserted(() -> assertThat(mailpitMessageCount()).isEqualTo(1)); // rejection only
    }
}
