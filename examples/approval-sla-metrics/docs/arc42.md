# arc42 Architecture Documentation — approval-sla-metrics

## 1 Introduction and Goals

This example shows how to make an Operaton BPMN process **observable**. A
purchase-requisition approval workflow is instrumented with Micrometer; Prometheus
scrapes the metrics and Grafana visualises SLA breaches, approval wait times and
throughput.

### Requirements overview
- Emit counters, a timer histogram and a gauge from BPMN listeners.
- Expose them on `/actuator/prometheus` and scrape with Prometheus.
- Provide a ready-to-run Grafana dashboard.
- Keep the dashboard live without manual interaction.

### Quality goals
1. **Observability** — every business-relevant event produces a metric.
2. **Zero-setup demo** — `docker compose up` + `mvnw spring-boot:run` gives live graphs.
3. **Standards compliance** — dual build, Testcontainers ITs, operaton namespace.

### Stakeholders
- Process developers learning to instrument Operaton.
- Operations engineers evaluating Operaton observability.

## 2 Architecture Constraints

- Pinned stack: Spring Boot 4.1.0, Operaton 2.1.1, JDK 21, PostgreSQL 16.
- Dual Maven + Gradle build; ITs via Testcontainers (no H2, no Thread.sleep).
- BPMN/DMN use the `operaton` namespace with full DI and `historyTimeToLive`.
- The application runs on the host; Docker Compose provides Postgres, Prometheus
  and Grafana only (per repository EXAMPLE_STANDARDS §6).
- Throwaway dev credentials only.

## 3 Context and Scope

### Business context
An employee (the load generator) raises purchase requisitions. Managers and
directors approve them within an SLA. The system measures how well the SLA is met.

### Technical context
- **App ⇄ PostgreSQL** — engine persistence (JDBC, localhost:5432).
- **Prometheus → App** — scrapes `host.docker.internal:8080/actuator/prometheus` every 5s.
- **Grafana → Prometheus** — queries `http://prometheus:9090`.
- **Browser → App** — Cockpit/Tasklist/Admin and the Actuator endpoint on :8080.
- **Browser → Grafana** — dashboard on :3000.

## 4 Solution Strategy

- **Instrument via listeners** — keep metric logic out of business code; BPMN
  listeners call a single `ApprovalMetrics` component that owns all meters.
- **Model the SLA in BPMN** — a non-interrupting boundary timer expresses the SLA
  deadline declaratively and records a breach without cancelling work.
- **Route with DMN** — amount → tier/SLA/group, so adding a tier is a table edit.
- **Self-drive the demo** — scheduled beans generate load and complete tasks with
  randomised timing so the dashboard always has data; gated by one config flag.
- **Provision Grafana from files** — datasource + dashboard ship in the repo.

## 5 Building Block View

### Level 1
- **ApprovalSlaMetricsApplication** — Spring Boot entry point (`@EnableScheduling`).
- **Process & DMN** — `purchase-requisition-approval.bpmn`, `purchase-requisition-routing.dmn`.
- **Metrics** — `ApprovalMetrics` (owns meters); `ApprovalTaskMetricsListener`,
  `SlaBreachDelegate`, `RequisitionOutcomeListener` (feed it).
- **Demo** — `RequisitionLoadGenerator`, `SimulatedReviewer`.
- **Identities** — `DataInitializer` (groups `managers`/`directors`, users alice/bob).

### Key interfaces
`ApprovalMetrics`: `recordWait(tier, outcome, duration)`, `incBreach(tier)`,
`incOutcome(tier, outcome)`, `incInProgress(tier)`, `decInProgress(tier)`.

Metrics emitted: `approval_wait_seconds` (Timer; tags tier, outcome),
`approval_sla_breaches_total` (Counter; tier), `requisitions_total` (Counter; tier,
outcome), `approvals_in_progress` (Gauge; tier).

## 6 Runtime View

### Manager-tier approval (happy path)
1. Instance starts with `amount` in [1000, 10000).
2. DMN sets tier=manager, slaDuration=PT5S, group=managers.
3. `approve-requisition` task created → `ApprovalTaskMetricsListener` records the
   create time and increments `approvals_in_progress{tier=manager}`.
4. Reviewer completes with `approved=true` → listener records
   `approval_wait_seconds{tier=manager,outcome=approved}` and decrements the gauge.
5. **Approved** end event → `RequisitionOutcomeListener` increments
   `requisitions_total{tier=manager,outcome=approved}`.

### Director-tier SLA breach (alternative path)
1. `amount` ≥ 10000 → tier=director, slaDuration=PT2S.
2. The task is not completed within 2s → the non-interrupting boundary timer fires.
3. `SlaBreachDelegate` increments `approval_sla_breaches_total{tier=director}` and
   sets `slaBreached=true`; the task remains open.
4. When the reviewer eventually completes, the wait timer and outcome counter record
   as usual.

### Auto tier
`amount` < 1000 → the gateway sets `approved=true` and routes straight to the
Approved end event; no user task, no gauge change.

## 7 Deployment View

The application runs on the host JVM (`./mvnw spring-boot:run`, port 8080). Docker
Compose (`docker compose up -d`) starts three containers:

| Container    | Image                    | Host port | Role                                |
|--------------|--------------------------|-----------|-------------------------------------|
| `postgres`   | `postgres:16-alpine`     | 5432      | Engine database                     |
| `prometheus` | `prom/prometheus:v3.1.0` | 9090      | Scrapes `host.docker.internal:8080` |
| `grafana`    | `grafana/grafana:11.4.0` | 3000      | Provisioned dashboard (anonymous)   |

Prometheus reaches the host app through the `host.docker.internal:host-gateway`
extra host (works on Docker Desktop and Linux). Grafana queries Prometheus over the
compose network at `http://prometheus:9090`. Datasource and dashboard are
file-provisioned from `grafana/provisioning/**` and `grafana/dashboards/`.

All services have healthchecks; Grafana waits for Prometheus to be healthy.

## 8 Cross-cutting Concepts

- **Metric naming** — meter names use Micrometer dot/underscore conventions; the
  Prometheus registry exposes the timer histogram buckets as
  `approval_wait_seconds_seconds_bucket`. The Grafana queries match this exactly.
- **Tagging** — every meter carries a `tier` tag; outcome-bearing meters add an
  `outcome` tag. A global `application` tag is set in `application.yaml`.
- **Time compression** — SLA durations are seconds for demonstration; documented in
  the README.
- **Demo isolation** — generator/reviewer beans are `@ConditionalOnProperty` so a
  real deployment (flag off) runs the process without synthetic load.

## 9 Architecture Decisions

### ADR-001: Instrument via BPMN listeners, not business code
Metrics are emitted from execution/task listeners calling a single `ApprovalMetrics`
component. Keeps instrumentation declarative and centralises meter definitions.

### ADR-002: Non-interrupting boundary timer for the SLA
The SLA deadline is modelled as a non-interrupting boundary timer so a breach is
recorded without cancelling the human task — matching real SLA semantics.

### ADR-003: App on host, infra in Compose
Per repository EXAMPLE_STANDARDS the app runs via `spring-boot:run`; Compose
provides Postgres + Prometheus + Grafana. Prometheus scrapes the host via
`host.docker.internal`.

### ADR-004: DMN outputs an ISO-8601 duration string
The routing table emits `slaDuration` (`PT5S`/`PT2S`) directly so the boundary
timer consumes it with no conversion code.

## 10 Quality Requirements and Risks

### Quality scenarios
- Starting the stack and app yields live Grafana panels within ~1 minute.
- Disabling the demo flag stops synthetic load with no other change.
- ITs prove process correctness and metric emission without Prometheus/Grafana.

### Risks
- **host.docker.internal on Linux** — mitigated by the `host-gateway` extra host.
- **Timer-job latency** — the breach IT waits up to 20s via Awaitility; the engine
  job executor must be enabled (default).
- **Histogram series naming** — the dashboard depends on Micrometer's
  `_seconds_bucket` suffix; keep the meter name in sync with the dashboard query.
