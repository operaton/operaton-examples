package org.operaton.examples.commandinterceptor;

import org.operaton.bpm.engine.impl.interceptor.Command;
import org.operaton.bpm.engine.impl.interceptor.CommandInterceptor;

public class CommandAuditInterceptor extends CommandInterceptor {

    private final CommandAuditLog auditLog;

    public CommandAuditInterceptor(CommandAuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public <T> T execute(Command<T> command) {
        long start = System.currentTimeMillis();
        try {
            return next.execute(command);
        } finally {
            auditLog.record(command.getClass().getSimpleName(), System.currentTimeMillis() - start);
        }
    }
}
