# Expense Reimbursement

An employee submits a receipt image; a **vision LLM** (via Spring delegate) verifies the receipt
matches the stated expense, a **FIRST-hit DMN table** decides whether manager approval is required,
an optional **user task** allows the finance team to approve or reject, a stub payment service
records the transaction, and the **LLM drafts a personalised email** sent via Spring Mail.

## What you will learn

- How to call a **multimodal vision LLM** from a Java delegate — encoding a `FileValue` as Base64
  and building a structured prompt with `PromptBuilder`
- How **process variables of type file** work: uploading via the embedded start form, reading bytes
  in `ReceiptAnalyzer`, and the fail-safe fallback to `UNRELATED` on any exception
- How a **three-way match result** (`MATCH` / `PARTIAL` / `UNRELATED`) feeds into a DMN decision
  to produce `approvalRequired`
- How a **DMN table with FIRST hit policy** works: the `UNRELATED` override row fires before the
  per-kind threshold rows, ensuring unverifiable receipts always route to a human
- How an **LLM drafts outcome emails** — two separate prompt strategies (approval vs. rejection
  tone) produce personalised email bodies sent via Spring Mail to Mailpit

## Process model

![Process diagram](src/main/resources/expense-reimbursement.png)

> To render the PNG yourself: `./scripts/render-bpmn.sh examples/use-cases/expense-reimbursement`
> (requires `npm install -g bpmn-to-image`).

## Prerequisites

- JDK 21
- Docker (recent version)

## Run it

```bash
docker compose up -d
./mvnw spring-boot:run
# or:
./gradlew bootRun
```

- Cockpit / Tasklist: http://localhost:8080 — **demo/demo**
- Mailpit (captured emails): http://localhost:8025
- Ollama API: http://localhost:11434

> **Hosted LLM override** — to point at any OpenAI-compatible endpoint instead of local Ollama:
> ```bash
> export LLM_BASE_URL=https://api.openai.com
> export LLM_API_KEY=sk-...
> export LLM_MODEL=gpt-4o-mini
> ```
> For a lighter local model use `LLM_MODEL=moondream` (~1.7 GB, vision-capable).
>
> The `docker-compose.yml` pulls `llama3.2-vision` by default via the `ollama-pull` init service.

## Walk through it

The start form is at http://localhost:8080 — open Tasklist, click **Start process**, then choose
**Expense Reimbursement**. Alternatively use the REST API below.

### Happy path — receipt matches, within tier (auto-approved)

```bash
# Submit a MEALS expense for €35 — below the €50 auto-approval threshold
curl -s -X POST http://localhost:8080/engine-rest/process-definition/key/expense-reimbursement/start \
  -u demo:demo \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "requesterName":  { "value": "Alice Berger",          "type": "String" },
      "requesterEmail": { "value": "alice@example.com",     "type": "String" },
      "kind":           { "value": "MEALS",                 "type": "String" },
      "statedCost":     { "value": 35.0,                    "type": "Double" },
      "reason":         { "value": "Team lunch",            "type": "String" }
    }
  }' | jq .id
```

The process completes automatically: `matchResult=MATCH`, `approvalRequired=false`, payment
reference set, approval email visible in Mailpit.

### Alternative path — UNRELATED receipt, finance approves

```bash
# Submit a TRAVEL expense — WireMock returns UNRELATED for "Bob Richter"
curl -s -X POST http://localhost:8080/engine-rest/process-definition/key/expense-reimbursement/start \
  -u demo:demo \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "requesterName":  { "value": "Bob Richter",           "type": "String" },
      "requesterEmail": { "value": "bob@example.com",       "type": "String" },
      "kind":           { "value": "TRAVEL",                "type": "String" },
      "statedCost":     { "value": 150.0,                   "type": "Double" },
      "reason":         { "value": "Conference travel",     "type": "String" }
    }
  }' | jq .id
```

Open Tasklist as `alice` (password: `alice`) — claim and approve the **Approve Reimbursement** task.
The process completes at *Expense Reimbursed* and Mailpit shows the approval email.

### Rejection path — over tier, finance rejects

```bash
# EQUIPMENT €1200 > €1000 threshold → approvalRequired=true
curl -s -X POST http://localhost:8080/engine-rest/process-definition/key/expense-reimbursement/start \
  -u demo:demo \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "requesterName":  { "value": "Charlie Weiss",         "type": "String" },
      "requesterEmail": { "value": "charlie@example.com",   "type": "String" },
      "kind":           { "value": "EQUIPMENT",             "type": "String" },
      "statedCost":     { "value": 1200.0,                  "type": "Double" },
      "reason":         { "value": "Laptop purchase",       "type": "String" }
    }
  }' | jq .id
```

Open Tasklist as `bob` — claim and **reject** the task. The process completes at *Expense Rejected*
and Mailpit shows the rejection email.

## How it works

| Model element | Code |
|---|---|
| `Analyze Receipt` (service task) | [`ReceiptAnalyzer`](src/main/java/org/operaton/examples/expensereimbursement/delegate/ReceiptAnalyzer.java) — reads the `receipt` `FileValue`, Base64-encodes it, calls `PromptBuilder.receiptAnalysisRequest`, parses `matchResult` / `extractedName` / `extractedCost` / `analysisNotes`; catches all exceptions and defaults to `UNRELATED` |
| `Decide on Approval` (business rule task) | [`reimbursement-approval.dmn`](src/main/resources/reimbursement-approval.dmn) — FIRST hit policy; first rule matches `UNRELATED` unconditionally; remaining rules compare `kind` and `statedCost` against per-category thresholds (MEALS ≤ €50, TRAVEL ≤ €250, ACCOMMODATION ≤ €400, EQUIPMENT ≤ €1,000); catch-all last row requires approval |
| `Approve Reimbursement` (user task) | `candidateGroups="finance"` — seed users `alice` and `bob` can claim it; the `approved` boolean variable drives the downstream gateway |
| `Perform Payment` (service task) | [`PaymentService`](src/main/java/org/operaton/examples/expensereimbursement/delegate/PaymentService.java) — simulates payment by generating a `PAY-` reference and recording `paymentDate` |
| `Draft Approval Email` (service task) | [`ApprovalEmailDrafter`](src/main/java/org/operaton/examples/expensereimbursement/delegate/ApprovalEmailDrafter.java) — calls LLM to draft a personalised approval email; falls back to a fixed template on error |
| `Draft Rejection Email` (service task) | [`RejectionEmailDrafter`](src/main/java/org/operaton/examples/expensereimbursement/delegate/RejectionEmailDrafter.java) — calls LLM to draft a personalised rejection email; same fail-safe pattern |
| `Notify Requester` (send task, both paths) | [`EmailDispatcher`](src/main/java/org/operaton/examples/expensereimbursement/EmailDispatcher.java) — reads `emailSubject` / `emailBody` / `requesterEmail` variables and sends via Spring `JavaMailSender` |

`PromptBuilder` constructs both the vision request (with the Base64 image embedded in the
`image_url` field) and the two email drafting prompts.
`ResponseParser` extracts JSON fields from the LLM response and provides safe defaults for
unparseable output.
`LlmClient` wraps a `RestTemplate` call to the configured `LLM_BASE_URL`; connection properties
are bound to `LlmProperties` (`llm.base-url`, `llm.model`, `llm.api-key`).

## Run the tests

```bash
./mvnw verify
# or:
./gradlew build
```

Three integration tests (`ExpenseReimbursementIT`) run against real PostgreSQL (Testcontainers),
the LLM stubbed by WireMock, and Mailpit as the SMTP sink: happy path (MEALS €35, auto-approved,
email sent), UNRELATED receipt then finance approval, and EQUIPMENT €1,200 over tier then
rejection — each asserts process end state, key variable values, and email delivery.
`ExpenseReimbursementDeploymentIT` verifies the BPMN and DMN deploy without errors.
