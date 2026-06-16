package org.operaton.examples.commandinterceptor;

import org.operaton.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.interceptor.CommandInterceptor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CommandAuditPlugin extends AbstractProcessEnginePlugin {

    private final CommandAuditLog auditLog;

    public CommandAuditPlugin(CommandAuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        List<CommandInterceptor> interceptors = configuration.getCustomPreCommandInterceptorsTxRequired();
        if (interceptors == null) {
            interceptors = new ArrayList<>();
            configuration.setCustomPreCommandInterceptorsTxRequired(interceptors);
        }
        interceptors.add(new CommandAuditInterceptor(auditLog));
    }
}
