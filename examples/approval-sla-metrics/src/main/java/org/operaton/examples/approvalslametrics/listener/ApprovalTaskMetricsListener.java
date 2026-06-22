package org.operaton.examples.approvalslametrics.listener;

import org.operaton.bpm.engine.delegate.DelegateTask;
import org.operaton.bpm.engine.delegate.TaskListener;
import org.operaton.examples.approvalslametrics.metrics.ApprovalMetrics;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Registered on the approval user task for both "create" and "complete" events. */
@Component
public class ApprovalTaskMetricsListener implements TaskListener {

    private final ApprovalMetrics metrics;

    public ApprovalTaskMetricsListener(ApprovalMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void notify(DelegateTask task) {
        String tier = (String) task.getVariable("approvalTier");
        switch (task.getEventName()) {
            case EVENTNAME_CREATE -> {
                task.setVariable("approvalCreatedAt", System.currentTimeMillis());
                metrics.incInProgress(tier);
            }
            case EVENTNAME_COMPLETE -> {
                Long createdAt = (Long) task.getVariable("approvalCreatedAt");
                long elapsed = createdAt == null ? 0L : System.currentTimeMillis() - createdAt;
                boolean approved = Boolean.TRUE.equals(task.getVariable("approved"));
                metrics.recordWait(tier, approved ? "approved" : "rejected", Duration.ofMillis(elapsed));
                metrics.decInProgress(tier);
            }
            default -> { /* no metric for other events */ }
        }
    }
}
