# 06 Runtime View — approval-sla-metrics

## Manager-tier approval (happy path)
1. Instance starts with `amount` in [1000, 10000).
2. DMN sets tier=manager, slaDuration=PT5S, group=managers.
3. `approve-requisition` task created → `ApprovalTaskMetricsListener` records the
   create time and increments `approvals_in_progress{tier=manager}`.
4. Reviewer completes with `approved=true` → listener records
   `approval_wait_seconds{tier=manager,outcome=approved}` and decrements the gauge.
5. **Approved** end event → `RequisitionOutcomeListener` increments
   `requisitions_total{tier=manager,outcome=approved}`.

## Director-tier SLA breach (alternative path)
1. `amount` ≥ 10000 → tier=director, slaDuration=PT2S.
2. The task is not completed within 2s → the non-interrupting boundary timer fires.
3. `SlaBreachDelegate` increments `approval_sla_breaches_total{tier=director}` and
   sets `slaBreached=true`; the task remains open.
4. When the reviewer eventually completes, the wait timer and outcome counter record
   as usual.

## Auto tier
`amount` < 1000 → the gateway sets `approved=true` and routes straight to the
Approved end event; no user task, no gauge change.
