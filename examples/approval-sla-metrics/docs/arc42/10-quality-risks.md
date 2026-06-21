# 10 Quality Requirements and Risks — approval-sla-metrics

## Quality scenarios
- Starting the stack and app yields live Grafana panels within ~1 minute.
- Disabling the demo flag stops synthetic load with no other change.
- ITs prove process correctness and metric emission without Prometheus/Grafana.

## Risks
- **host.docker.internal on Linux** — mitigated by the `host-gateway` extra host.
- **Timer-job latency** — the breach IT waits up to 20s via Awaitility; the engine
  job executor must be enabled (default).
- **Histogram series naming** — the dashboard depends on Micrometer's
  `_seconds_bucket` suffix; keep the meter name in sync with the dashboard query.
