package org.operaton.examples.externaltaskworker;

import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShippingHandler implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        // A real implementation would call a carrier API here; simplified for the example
        externalTaskService.complete(externalTask,
            Map.of("trackingId", "TRK-" + externalTask.getId()));
    }
}
