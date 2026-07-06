package org.operaton.examples.approvalslametrics.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns every Micrometer meter the approval process emits. */
@Component
public class ApprovalMetrics {

    private final MeterRegistry registry;
    private final Map<String, AtomicInteger> inProgress = new ConcurrentHashMap<>();

    public ApprovalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordWait(String tier, String outcome, Duration duration) {
        registry.timer("approval_wait_seconds", "tier", tier, "outcome", outcome).record(duration);
    }

    public void incBreach(String tier) {
        registry.counter("approval_sla_breaches_total", "tier", tier).increment();
    }

    public void incOutcome(String tier, String outcome) {
        registry.counter("requisitions_total", "tier", tier, "outcome", outcome).increment();
    }

    public void incInProgress(String tier) {
        gauge(tier).incrementAndGet();
    }

    public void decInProgress(String tier) {
        gauge(tier).decrementAndGet();
    }

    private AtomicInteger gauge(String tier) {
        return inProgress.computeIfAbsent(tier,
                t -> registry.gauge("approvals_in_progress", Tags.of("tier", t), new AtomicInteger(0)));
    }
}
