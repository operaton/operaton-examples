# 01 Introduction and Goals — approval-sla-metrics

This example shows how to make an Operaton BPMN process **observable**. A
purchase-requisition approval workflow is instrumented with Micrometer; Prometheus
scrapes the metrics and Grafana visualises SLA breaches, approval wait times and
throughput.

## Requirements overview
- Emit counters, a timer histogram and a gauge from BPMN listeners.
- Expose them on `/actuator/prometheus` and scrape with Prometheus.
- Provide a ready-to-run Grafana dashboard.
- Keep the dashboard live without manual interaction.

## Quality goals
1. **Observability** — every business-relevant event produces a metric.
2. **Zero-setup demo** — `docker compose up` + `mvnw spring-boot:run` gives live graphs.
3. **Standards compliance** — dual build, Testcontainers ITs, operaton namespace.

## Stakeholders
- Process developers learning to instrument Operaton.
- Operations engineers evaluating Operaton observability.
