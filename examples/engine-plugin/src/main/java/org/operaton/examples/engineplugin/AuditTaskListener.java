package org.operaton.examples.engineplugin;

import org.operaton.bpm.engine.delegate.DelegateTask;
import org.operaton.bpm.engine.delegate.TaskListener;

public class AuditTaskListener implements TaskListener {

    private final AuditLog auditLog;

    public AuditTaskListener(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        if (EVENTNAME_COMPLETE.equals(delegateTask.getEventName())) {
            auditLog.record(delegateTask.getId(), delegateTask.getName(), delegateTask.getAssignee());
        }
    }
}
