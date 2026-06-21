# 09 Architecture Decisions — approval-sla-metrics

## ADR-001: Instrument via BPMN listeners, not business code
Metrics are emitted from execution/task listeners calling a single `ApprovalMetrics`
component. Keeps instrumentation declarative and centralises meter definitions.

## ADR-002: Non-interrupting boundary timer for the SLA
The SLA deadline is modelled as a non-interrupting boundary timer so a breach is
recorded without cancelling the human task — matching real SLA semantics.

## ADR-003: App on host, infra in Compose
Per repository EXAMPLE_STANDARDS the app runs via `spring-boot:run`; Compose
provides Postgres + Prometheus + Grafana. Prometheus scrapes the host via
`host.docker.internal`.

## ADR-004: DMN outputs an ISO-8601 duration string
The routing table emits `slaDuration` (`PT5S`/`PT2S`) directly so the boundary
timer consumes it with no conversion code.
