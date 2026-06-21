# 08 Cross-cutting Concepts — approval-sla-metrics

- **Metric naming** — meter names use Micrometer dot/underscore conventions; the
  Prometheus registry exposes the timer histogram buckets as
  `approval_wait_seconds_seconds_bucket`. The Grafana queries match this exactly.
- **Tagging** — every meter carries a `tier` tag; outcome-bearing meters add an
  `outcome` tag. A global `application` tag is set in `application.yaml`.
- **Time compression** — SLA durations are seconds for demonstration; documented in
  the README.
- **Demo isolation** — generator/reviewer beans are `@ConditionalOnProperty` so a
  real deployment (flag off) runs the process without synthetic load.
