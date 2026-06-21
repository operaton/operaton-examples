package org.operaton.examples.approvalslametrics.demo;

import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Demo only: starts a requisition every couple of seconds with an amount spanning all tiers. */
@Component
@ConditionalOnProperty(name = "demo.load-generator.enabled", havingValue = "true")
public class RequisitionLoadGenerator {

    private final RuntimeService runtimeService;

    public RequisitionLoadGenerator(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Scheduled(fixedDelay = 2000)
    public void startRequisition() {
        double amount = switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> ThreadLocalRandom.current().nextDouble(100, 1000);    // auto
            case 1 -> ThreadLocalRandom.current().nextDouble(1000, 10000);  // manager
            default -> ThreadLocalRandom.current().nextDouble(10000, 50000); // director
        };
        runtimeService.startProcessInstanceByKey("purchase-requisition-approval",
                Variables.putValue("amount", amount)
                        .putValue("requesterId", "emp-" + ThreadLocalRandom.current().nextInt(1000)));
    }
}
