# 05 Building Block View — approval-sla-metrics

## Level 1
- **ApprovalSlaMetricsApplication** — Spring Boot entry point (`@EnableScheduling`).
- **Process & DMN** — `purchase-requisition-approval.bpmn`, `purchase-requisition-routing.dmn`.
- **Metrics** — `ApprovalMetrics` (owns meters); `ApprovalTaskMetricsListener`,
  `SlaBreachDelegate`, `RequisitionOutcomeListener` (feed it).
- **Demo** — `RequisitionLoadGenerator`, `SimulatedReviewer`.
- **Identities** — `DataInitializer` (groups `managers`/`directors`, users alice/bob).

## Key interfaces
`ApprovalMetrics`: `recordWait(tier, outcome, duration)`, `incBreach(tier)`,
`incOutcome(tier, outcome)`, `incInProgress(tier)`, `decInProgress(tier)`.

Metrics emitted: `approval_wait_seconds` (Timer; tags tier, outcome),
`approval_sla_breaches_total` (Counter; tier), `requisitions_total` (Counter; tier,
outcome), `approvals_in_progress` (Gauge; tier).
