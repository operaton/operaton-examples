package org.operaton.examples.approvalslametrics.listener;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.ExecutionListener;
import org.operaton.examples.approvalslametrics.metrics.ApprovalMetrics;
import org.springframework.stereotype.Component;

/** Registered as a "start" execution listener on both end events. */
@Component
public class RequisitionOutcomeListener implements ExecutionListener {

    private final ApprovalMetrics metrics;

    public RequisitionOutcomeListener(ApprovalMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void notify(DelegateExecution execution) {
        String tier = (String) execution.getVariable("approvalTier");
        boolean approved = Boolean.TRUE.equals(execution.getVariable("approved"));
        metrics.incOutcome(tier, approved ? "approved" : "rejected");
    }
}
