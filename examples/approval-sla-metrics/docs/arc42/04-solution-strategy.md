# 04 Solution Strategy — approval-sla-metrics

- **Instrument via listeners** — keep metric logic out of business code; BPMN
  listeners call a single `ApprovalMetrics` component that owns all meters.
- **Model the SLA in BPMN** — a non-interrupting boundary timer expresses the SLA
  deadline declaratively and records a breach without cancelling work.
- **Route with DMN** — amount → tier/SLA/group, so adding a tier is a table edit.
- **Self-drive the demo** — scheduled beans generate load and complete tasks with
  randomised timing so the dashboard always has data; gated by one config flag.
- **Provision Grafana from files** — datasource + dashboard ship in the repo.
