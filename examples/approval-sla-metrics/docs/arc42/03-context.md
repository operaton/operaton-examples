# 03 Context and Scope — approval-sla-metrics

## Business context
An employee (the load generator) raises purchase requisitions. Managers and
directors approve them within an SLA. The system measures how well the SLA is met.

## Technical context
- **App ⇄ PostgreSQL** — engine persistence (JDBC, localhost:5432).
- **Prometheus → App** — scrapes `host.docker.internal:8080/actuator/prometheus` every 5s.
- **Grafana → Prometheus** — queries `http://prometheus:9090`.
- **Browser → App** — Cockpit/Tasklist/Admin and the Actuator endpoint on :8080.
- **Browser → Grafana** — dashboard on :3000.
