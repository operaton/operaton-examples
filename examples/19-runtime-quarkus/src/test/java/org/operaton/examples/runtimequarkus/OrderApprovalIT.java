package org.operaton.examples.runtimequarkus;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.runtime.ProcessInstance;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class OrderApprovalIT {

    @Inject
    ProcessEngine processEngine;

    @Test
    void lowValueOrderIsApproved() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        HistoryService historyService = processEngine.getHistoryService();

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "order-approval",
            Map.of("orderTotal", 500.0));

        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();

        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Approved");
    }

    @Test
    void highValueOrderIsRejected() {
        RuntimeService runtimeService = processEngine.getRuntimeService();
        HistoryService historyService = processEngine.getHistoryService();

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "order-approval",
            Map.of("orderTotal", 1500.0));

        HistoricProcessInstance historic = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .singleResult();

        assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
        assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_Rejected");
    }
}
