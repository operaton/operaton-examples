package org.operaton.examples.engineplugin;

import org.operaton.bpm.engine.delegate.TaskListener;
import org.operaton.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.operaton.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.operaton.bpm.engine.impl.pvm.process.ActivityImpl;
import org.operaton.bpm.engine.impl.pvm.process.ScopeImpl;
import org.operaton.bpm.engine.impl.task.TaskDefinition;
import org.operaton.bpm.engine.impl.util.xml.Element;

public class AuditBpmnParseListener extends AbstractBpmnParseListener {

    private final AuditLog auditLog;

    public AuditBpmnParseListener(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
        TaskDefinition taskDefinition =
                ((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition();
        taskDefinition.addTaskListener(TaskListener.EVENTNAME_COMPLETE, new AuditTaskListener(auditLog));
    }
}
