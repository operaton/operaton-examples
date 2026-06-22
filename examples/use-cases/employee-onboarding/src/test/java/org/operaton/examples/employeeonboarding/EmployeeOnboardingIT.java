package org.operaton.examples.employeeonboarding;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class EmployeeOnboardingIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void processDefinitionsAreDeployed() {
        var repoSvc = processEngine.getRepositoryService();
        assertThat(repoSvc.createProcessDefinitionQuery()
            .processDefinitionKey("employee-onboarding").count()).isEqualTo(1);
        assertThat(repoSvc.createProcessDefinitionQuery()
            .processDefinitionKey("provision-equipment").count()).isEqualTo(1);
        assertThat(repoSvc.createProcessDefinitionQuery()
            .processDefinitionKey("grant-system-access").count()).isEqualTo(1);
    }

    @Test
    void happyPath_equipmentProvisioned_accessGranted_onboards() {
        // role=engineer (granted), default equipment list of 3 items
        var pi = runtimeService.startProcessInstanceByKey(
            "employee-onboarding",
            "EMP-001",
            Variables.createVariables()
                .putValue("employeeId", "EMP-001")
                .putValue("role", "engineer")
                .putValue("equipmentList", List.of("laptop", "phone", "badge")));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Onboarded");
        });

        // Three provision-equipment child instances were created (one per item)
        long provisionCount = historyService.createHistoricProcessInstanceQuery()
            .superProcessInstanceId(pi.getId())
            .processDefinitionKey("provision-equipment")
            .count();
        assertThat(provisionCount).isEqualTo(3);

        // access was granted
        var accessGranted = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("accessGranted").singleResult();
        assertThat(accessGranted).isNotNull();
        assertThat((Boolean) accessGranted.getValue()).isTrue();

        // not flagged for IT review
        assertThat(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("itReviewRequired").singleResult()).isNull();
    }

    @Test
    void restrictedRole_accessDenied_routesToManualRemediation() {
        // role=restricted — grantAccessDelegate sets accessGranted=false
        var pi = runtimeService.startProcessInstanceByKey(
            "employee-onboarding",
            "EMP-002",
            Variables.createVariables()
                .putValue("employeeId", "EMP-002")
                .putValue("role", "restricted")
                .putValue("equipmentList", List.of("laptop")));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ManualRemediation");
        });

        // flagged for IT review
        var itReviewRequired = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("itReviewRequired").singleResult();
        assertThat(itReviewRequired).isNotNull();
        assertThat((Boolean) itReviewRequired.getValue()).isTrue();

        // access was denied
        var accessGranted = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("accessGranted").singleResult();
        assertThat(accessGranted).isNotNull();
        assertThat((Boolean) accessGranted.getValue()).isFalse();
    }

    @Test
    void multiInstance_createsOneChildPerEquipmentItem() {
        // custom 2-item list — verifies N child processes == N items
        var pi = runtimeService.startProcessInstanceByKey(
            "employee-onboarding",
            "EMP-003",
            Variables.createVariables()
                .putValue("employeeId", "EMP-003")
                .putValue("role", "engineer")
                .putValue("equipmentList", List.of("keyboard", "monitor")));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        });

        // Exactly 2 provision-equipment child instances (one per item in the 2-item list)
        long provisionCount = historyService.createHistoricProcessInstanceQuery()
            .superProcessInstanceId(pi.getId())
            .processDefinitionKey("provision-equipment")
            .count();
        assertThat(provisionCount).isEqualTo(2);
    }

    @Test
    void nullEquipmentList_defaultsToThreeItems_onboards() {
        // No equipmentList provided — PrepareOnboardingDelegate sets the default 3-item list
        var pi = runtimeService.startProcessInstanceByKey(
            "employee-onboarding",
            "EMP-004",
            Variables.createVariables()
                .putValue("employeeId", "EMP-004")
                .putValue("role", "engineer"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Onboarded");
        });

        // Default list has 3 items → 3 provision-equipment children
        long provisionCount = historyService.createHistoricProcessInstanceQuery()
            .superProcessInstanceId(pi.getId())
            .processDefinitionKey("provision-equipment")
            .count();
        assertThat(provisionCount).isEqualTo(3);
    }
}
