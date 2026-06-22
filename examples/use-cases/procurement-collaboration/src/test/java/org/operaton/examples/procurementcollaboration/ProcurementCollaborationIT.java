package org.operaton.examples.procurementcollaboration;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProcurementCollaborationIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void processDefinitionsAreDeployed() {
        var repo = processEngine.getRepositoryService();
        assertThat(repo.createProcessDefinitionQuery()
            .processDefinitionKey("purchase-request").count()).isEqualTo(1);
        assertThat(repo.createProcessDefinitionQuery()
            .processDefinitionKey("quote-handling").count()).isEqualTo(1);
    }

    @Test
    void withinBudget_orderIsPlaced_supplierReservesStock() {
        var pi = runtimeService.startProcessInstanceByKey("purchase-request",
            Variables.createVariables()
                .putValue("requestId", "REQ-OK")
                .putValue("item", "widget")
                .putValue("quantity", 10)
                .putValue("maxBudget", 5000));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var buyer = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId()).singleResult();
            assertThat(buyer.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(buyer.getEndActivityId()).isEqualTo("EndEvent_DecisionSent");
        });

        var accepted = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("accepted").singleResult().getValue();
        assertThat(accepted).isEqualTo(true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var supplier = findSupplierInstance("REQ-OK");
            assertThat(supplier.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(supplier.getEndActivityId()).isEqualTo("EndEvent_OrderFulfilled");

            var reserved = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(supplier.getId()).variableName("reserved").singleResult().getValue();
            assertThat(reserved).isEqualTo(true);
        });
    }

    @Test
    void overBudget_orderDeclined_supplierReleasesQuote() {
        var pi = runtimeService.startProcessInstanceByKey("purchase-request",
            Variables.createVariables()
                .putValue("requestId", "REQ-OVER")
                .putValue("item", "widget")
                .putValue("quantity", 10)
                .putValue("maxBudget", 500));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var buyer = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId()).singleResult();
            assertThat(buyer.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        });

        var accepted = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("accepted").singleResult().getValue();
        assertThat(accepted).isEqualTo(false);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var supplier = findSupplierInstance("REQ-OVER");
            assertThat(supplier.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(supplier.getEndActivityId()).isEqualTo("EndEvent_QuoteReleased");

            var reservedVar = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(supplier.getId()).variableName("reserved").singleResult();
            assertThat(reservedVar).isNull();
        });
    }

    @Test
    void correlation_linksResponseToOriginatingBuyer() {
        var piA = runtimeService.startProcessInstanceByKey("purchase-request",
            Variables.createVariables()
                .putValue("requestId", "REQ-A")
                .putValue("item", "widget")
                .putValue("quantity", 1)
                .putValue("maxBudget", 5000));
        var piB = runtimeService.startProcessInstanceByKey("purchase-request",
            Variables.createVariables()
                .putValue("requestId", "REQ-B")
                .putValue("item", "widget")
                .putValue("quantity", 5)
                .putValue("maxBudget", 5000));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var buyerA = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(piA.getId()).singleResult();
            var buyerB = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(piB.getId()).singleResult();
            assertThat(buyerA.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(buyerB.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        });

        // Each buyer received its OWN totalPrice — messages did not cross
        var totalPriceA = (Integer) historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(piA.getId()).variableName("totalPrice").singleResult().getValue();
        var totalPriceB = (Integer) historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(piB.getId()).variableName("totalPrice").singleResult().getValue();
        assertThat(totalPriceA).isEqualTo(100);  // 1 × 100
        assertThat(totalPriceB).isEqualTo(500);  // 5 × 100

        // Two separate supplier instances ran for this test's requestIds
        assertThat(findSupplierInstance("REQ-A")).isNotNull();
        assertThat(findSupplierInstance("REQ-B")).isNotNull();
    }

    // The two processes are not parent/child (no call activity), so there is no
    // superProcessInstanceId link; locate by the requestId variable stored on each supplier instance.
    private HistoricProcessInstance findSupplierInstance(String requestId) {
        var supplierInstances = historyService.createHistoricProcessInstanceQuery()
            .processDefinitionKey("quote-handling")
            .list();
        return supplierInstances.stream()
            .filter(hi -> {
                var v = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(hi.getId())
                    .variableName("requestId")
                    .singleResult();
                return v != null && requestId.equals(v.getValue());
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No quote-handling instance found for requestId=" + requestId));
    }
}
