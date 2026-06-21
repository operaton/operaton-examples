# 02 Architecture Constraints — approval-sla-metrics

- Pinned stack: Spring Boot 4.1.0, Operaton 2.1.1, JDK 21, PostgreSQL 16.
- Dual Maven + Gradle build; ITs via Testcontainers (no H2, no Thread.sleep).
- BPMN/DMN use the `operaton` namespace with full DI and `historyTimeToLive`.
- The application runs on the host; Docker Compose provides Postgres, Prometheus
  and Grafana only (per repository EXAMPLE_STANDARDS §6).
- Throwaway dev credentials only.
