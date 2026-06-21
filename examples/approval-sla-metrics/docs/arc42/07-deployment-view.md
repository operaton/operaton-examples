# 07 Deployment View — approval-sla-metrics

The application runs on the host JVM (`./mvnw spring-boot:run`, port 8080). Docker
Compose (`docker compose up -d`) starts three containers:

| Container   | Image                     | Host port | Role                                   |
|-------------|---------------------------|-----------|----------------------------------------|
| `postgres`  | `postgres:16-alpine`      | 5432      | Engine database                        |
| `prometheus`| `prom/prometheus:v3.1.0`  | 9090      | Scrapes `host.docker.internal:8080`    |
| `grafana`   | `grafana/grafana:11.4.0`  | 3000      | Provisioned dashboard (anonymous view) |

Prometheus reaches the host app through the `host.docker.internal:host-gateway`
extra host (works on Docker Desktop and Linux). Grafana queries Prometheus over the
compose network at `http://prometheus:9090`. Datasource and dashboard are
file-provisioned from `grafana/provisioning/**` and `grafana/dashboards/`.

All services have healthchecks; Grafana waits for Prometheus to be healthy.
