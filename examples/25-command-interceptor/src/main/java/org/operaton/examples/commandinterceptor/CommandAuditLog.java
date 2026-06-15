package org.operaton.examples.commandinterceptor;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class CommandAuditLog {

    public record AuditEntry(String commandName, long durationMs) {}

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    public void record(String commandName, long durationMs) {
        entries.add(new AuditEntry(commandName, durationMs));
    }

    public List<AuditEntry> getEntries() {
        return entries;
    }

    public void clear() {
        entries.clear();
    }
}
