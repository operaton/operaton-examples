package org.operaton.examples.approvalslametrics.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApprovalMetrics metrics = new ApprovalMetrics(registry);

    @Test
    void recordsWaitTimer() {
        metrics.recordWait("manager", "approved", Duration.ofSeconds(3));
        var timer = registry.find("approval_wait_seconds")
                .tag("tier", "manager").tag("outcome", "approved").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(3.0);
    }

    @Test
    void countsBreachesByTier() {
        metrics.incBreach("director");
        metrics.incBreach("director");
        assertThat(registry.find("approval_sla_breaches_total").tag("tier", "director").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    void countsOutcomes() {
        metrics.incOutcome("manager", "approved");
        metrics.incOutcome("manager", "rejected");
        assertThat(registry.find("requisitions_total").tag("tier", "manager").tag("outcome", "approved").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("requisitions_total").tag("tier", "manager").tag("outcome", "rejected").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void tracksInProgressGauge() {
        metrics.incInProgress("director");
        metrics.incInProgress("director");
        metrics.decInProgress("director");
        assertThat(registry.find("approvals_in_progress").tag("tier", "director").gauge().value())
                .isEqualTo(1.0);
    }
}
