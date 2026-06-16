package org.operaton.examples.engineplugin;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AuditLog {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    public void record(String taskId, String taskName, String assignee) {
        entries.add(new AuditEntry(taskId, taskName, assignee));
    }

    public List<AuditEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public record AuditEntry(String taskId, String taskName, String assignee) {}
}
