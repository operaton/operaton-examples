package org.operaton.examples.approvalslametrics.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.examples.approvalslametrics.metrics.ApprovalMetrics;
import org.springframework.stereotype.Component;

/** Runs on the non-interrupting boundary-timer path when an approval breaches its SLA. */
@Component
public class SlaBreachDelegate implements JavaDelegate {

    private final ApprovalMetrics metrics;

    public SlaBreachDelegate(ApprovalMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String tier = (String) execution.getVariable("approvalTier");
        execution.setVariable("slaBreached", true);
        metrics.incBreach(tier);
    }
}
