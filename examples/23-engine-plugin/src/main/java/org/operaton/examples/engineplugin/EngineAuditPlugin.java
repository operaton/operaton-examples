package org.operaton.examples.engineplugin;

import org.operaton.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.operaton.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EngineAuditPlugin extends AbstractProcessEnginePlugin {

    private final AuditLog auditLog;

    public EngineAuditPlugin(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        List<BpmnParseListener> listeners = configuration.getCustomPreBPMNParseListeners();
        if (listeners == null) {
            listeners = new ArrayList<>();
            configuration.setCustomPreBPMNParseListeners(listeners);
        }
        listeners.add(new AuditBpmnParseListener(auditLog));
    }
}
