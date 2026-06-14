package org.operaton.examples.externaltaskworker;

import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InventoryCheckHandler implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        if (Boolean.TRUE.equals(externalTask.getVariable("simulateOutOfStock"))) {
            // BpmnError routes to the OUT_OF_STOCK boundary event — a business decision, not a retry
            externalTaskService.handleBpmnError(externalTask, "OUT_OF_STOCK",
                "Requested item is out of stock");
        } else {
            externalTaskService.complete(externalTask,
                Map.of("reservationId", "RES-" + externalTask.getId()));
        }
    }
}
