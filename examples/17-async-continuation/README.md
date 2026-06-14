# Example 17 — Async Continuation

This example demonstrates **async continuations** in Operaton: using `operaton:asyncBefore` on service tasks to introduce transaction boundaries so that each task is persisted to the database and executed by the job executor independently.

## What you will learn

- How `operaton:asyncBefore` turns a service task into a job — the engine persists state and continues execution via the job executor
- How transaction boundaries isolate each service task into its own database transaction
- How to use `ManagementService.executeJob()` in tests to drive jobs manually, without waiting for the background job executor
- How `operaton:failedJobRetryTimeCycle` (e.g. `R3/PT0S`) configures automatic retry behaviour on transient failures
- How to disable the background job executor in Spring Boot tests with `operaton.bpm.job-execution.enabled=false`

## Process model

```mermaid
flowchart LR
    Start([Report requested]) --> Fetch[Fetch data\nasyncBefore]
    Fetch --> Process[Process data\nasyncBefore\nR3/PT0S retries]
    Process --> Store[Store report\nasyncBefore]
    Store --> End([Report ready])

    style Fetch fill:#e3f2fd
    style Process fill:#e3f2fd
    style Store fill:#e3f2fd
```

Each shaded task has `operaton:asyncBefore="true"`, meaning the engine creates a job in the database before executing the task. The job executor picks up that job and runs the delegate in a new transaction.

## Prerequisites

- JDK 21
- Docker (for Testcontainers and local run)

## Run it

```bash
# Start PostgreSQL
docker compose up -d

# Run the application (Cockpit + Tasklist available)
./mvnw spring-boot:run
# or:
./gradlew bootRun
```

Cockpit / Tasklist: http://localhost:8080  
Credentials: `demo` / `demo`

To start a process instance via the REST API:

```bash
curl -s -u demo:demo -X POST http://localhost:8080/engine-rest/process-definition/key/report-generation/start \
  -H "Content-Type: application/json" \
  -d '{"variables": {"failTwice": {"value": false, "type": "Boolean"}}}'
```

## Walk through it

### Happy path — all three tasks succeed

1. Start a process instance via the REST API (see above, `failTwice=false`).
2. Open Cockpit → Running instances → `report-generation`. The instance shows one **job** waiting at **Fetch data**.
3. The background job executor will pick up and execute each job in sequence, progressing through Fetch data → Process data → Store report → Report ready.
4. Refresh Cockpit — the instance appears under **Completed** once all three jobs have executed.

### Retry path — transient failure on Process data

1. Start a process instance with `failTwice=true`.
2. Cockpit shows the instance stuck at **Process data** after two failures (retries decrement from 3 to 1).
3. Once the third attempt succeeds, execution continues to Store report and completes.

## How it works

**BPMN** ([`report-generation.bpmn`](src/main/resources/report-generation.bpmn)):  
Each service task carries `operaton:asyncBefore="true"`. When the engine token arrives at the task, it does **not** execute the delegate immediately — instead it persists a `ACT_RU_JOB` record and commits the transaction. The job executor picks up that record and executes the delegate in a fresh transaction.

`Task_ProcessData` additionally declares:
```xml
<operaton:failedJobRetryTimeCycle>R3/PT0S</operaton:failedJobRetryTimeCycle>
```
This means up to 3 total attempts (`R3`) with zero wait between retries (`PT0S`). On each failure the engine decrements `RETRIES_` in `ACT_RU_JOB`. When retries reach 0 the job moves to the failed-job state for manual incident resolution.

**Delegates** live in [`src/main/java/…/delegate/`](src/main/java/org/operaton/examples/asynccontinuation/delegate/):
- `FetchDataDelegate` — sets `dataFetched=true`, `recordCount=42`
- `ProcessDataDelegate` — simulates transient failure when `failTwice=true` (throws on first two invocations per process instance, then succeeds)
- `StoreReportDelegate` — sets `reportStored=true`

## Run the tests

```bash
./mvnw verify
# or:
./gradlew build
```

The integration tests (`ReportGenerationProcessIT`) disable the background job executor (`operaton.bpm.job-execution.enabled=false`) and drive jobs manually via `ManagementService.executeJob()`. They prove:
1. **Happy path**: all three async tasks progress correctly when jobs are executed in sequence.
2. **Retry path**: `Task_ProcessData` decrements retries on each failure and succeeds on the third attempt.
3. **Visibility**: a freshly started instance exposes exactly one pending job.
