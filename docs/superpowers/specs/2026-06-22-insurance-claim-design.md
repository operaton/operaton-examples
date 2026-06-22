# Insurance Claim — Design Spec

## Goal

Implement `uc-06-insurance-claim`: a use-case example showcasing the
**event-based gateway** (first appearance in the catalog) and the **parallel
gateway** (AND fork/join — also absent from all existing examples). An
insurance company receives a damage claim, requests supporting documents,
runs fraud check and damage appraisal concurrently, then settles or rejects
via DMN.

---

## Location and Coordinates

| Field | Value |
|---|---|
| Directory | `examples/use-cases/insurance-claim` |
| Maven artifactId | `uc-06-insurance-claim` |
| Java package | `org.operaton.examples.insuranceclaim` |
| BPMN process id | `insurance-claim` |
| DMN decision id | `claim-settlement` |

Parent: `operaton-examples-aggregate` (same as all other use cases).

Registration: add to `pom.xml` `<modules>` and `settings.gradle.kts` in the
`operaton-examples` root.

---

## Process Flow

Business key: `claimNumber`.

```
(Start: "Claim submitted")
  → [Service] "Request supporting documents"         requestDocumentsDelegate
  → [Event-based gateway] "Await documents"
      ├─ [Message catch] "Documents received"         correlatedBy: claimNumber
      │     → [Parallel gateway] "Run assessments" (AND-split)
      │           ├─ [Service] "Fraud check"           fraudCheckDelegate
      │           │              sets: fraudSuspected
      │           └─ [Service] "Appraise damage"       appraiseDamageDelegate
      │                          sets: appraisedAmount
      │     → [Parallel gateway] (AND-join)
      │     → [Business Rule] "Settle claim"           DMN claim-settlement
      │           sets: settlementDecision, approvedAmount
      │     → [Exclusive gateway] "Approved?"
      │           ├─ approve   → (End: "Claim settled")
      │           └─ otherwise → (End: "Claim rejected")
      └─ [Timer catch] "Submission deadline"          ${documentDeadline}
            → [Service] "Close incomplete claim"       closeIncompleteClaimDelegate
            → (End: "Claim closed — documents not received")
```

### Process variables

| Variable | Type | Set by | Notes |
|---|---|---|---|
| `claimNumber` | String | caller | business key |
| `policyNumber` | String | caller | stored for record |
| `claimType` | String | caller | `"collision"`, `"flood"`, `"fire"`, … |
| `estimatedAmount` | Double | caller | claimant's estimate |
| `documentDeadline` | String | caller | ISO-8601 duration, default `P14D` |
| `fraudSuspected` | Boolean | `fraudCheckDelegate` | `estimatedAmount > 100000` |
| `appraisedAmount` | Double | `appraiseDamageDelegate` | `estimatedAmount * 0.9` |
| `settlementDecision` | String | DMN | `"approve"` or `"reject"` |
| `approvedAmount` | Double | DMN | amount to pay out |

---

## DMN — `claim-settlement.dmn`

**Decision id:** `claim-settlement`
**Hit policy:** FIRST
**Outputs:** `settlementDecision` (String), `approvedAmount` (Double)
**Inputs:** `fraudSuspected` (Boolean), `claimType` (String), `appraisedAmount` (Double)
**Output expressions:** FEEL

| fraudSuspected | claimType | appraisedAmount | settlementDecision | approvedAmount |
|---|---|---|---|---|
| `true` | — | — | `"reject"` | `0` |
| — | `"flood"` | — | `"reject"` | `0` |
| — | — | `<= 1000` | `"approve"` | `appraisedAmount` |
| — | — | `<= 50000` | `"approve"` | `appraisedAmount * 0.8` |
| — | — | — | `"reject"` | `0` |

---

## Delegate Implementations

All are Spring beans (`@Component`) implementing `JavaDelegate`.
No stubs — all produce realistic side-effect-free behavior.

### `RequestDocumentsDelegate` (`requestDocumentsDelegate`)

Logs: `"Requesting documents for claim [claimNumber] from policy [policyNumber]"`.
No external I/O.

### `FraudCheckDelegate` (`fraudCheckDelegate`)

Sets `fraudSuspected = (estimatedAmount > 100_000)`.
Logs result.

### `AppraiseDamageDelegate` (`appraiseDamageDelegate`)

Sets `appraisedAmount = estimatedAmount * 0.9` (appraiser adjusts down 10%).
Logs result.

### `CloseIncompleteClaimDelegate` (`closeIncompleteClaimDelegate`)

Logs: `"Closing claim [claimNumber] — no documents received within deadline"`.
No external I/O.

---

## Module Structure

```
examples/use-cases/insurance-claim/
├── mvnw, mvnw.cmd, .mvn/wrapper/
├── pom.xml
├── gradlew, gradlew.bat, gradle/wrapper/
├── build.gradle.kts, settings.gradle.kts
├── docker-compose.yml
├── README.md
└── src/
    ├── main/
    │   ├── java/org/operaton/examples/insuranceclaim/
    │   │   ├── InsuranceClaimApplication.java
    │   │   ├── RequestDocumentsDelegate.java
    │   │   ├── FraudCheckDelegate.java
    │   │   ├── AppraiseDamageDelegate.java
    │   │   └── CloseIncompleteClaimDelegate.java
    │   └── resources/
    │       ├── insurance-claim.bpmn
    │       ├── claim-settlement.dmn
    │       └── application.yaml
    └── test/
        └── java/org/operaton/examples/insuranceclaim/
            └── InsuranceClaimIT.java
```

---

## Build Configuration

Spring Boot 4.1.0, Operaton 2.1.1, Java 21. Same BOM pattern as all other
use cases (`spring-boot-dependencies` + `operaton-bom`). Dual build: Maven
failsafe runs `*IT`, Gradle standard test task discovers them.

---

## docker-compose.yml

PostgreSQL only (no external services required). App runs on host via
`./mvnw spring-boot:run` or `./gradlew bootRun`.

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: operaton
      POSTGRES_USER: operaton
      POSTGRES_PASSWORD: operaton
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U operaton"]
      interval: 5s
      timeout: 5s
      retries: 5
```

---

## application.yaml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/operaton
    username: operaton
    password: operaton

operaton.bpm:
  admin-user:
    id: demo
    password: demo
    first-name: Demo
  filter:
    create: All tasks
```

No groups or additional users seeded (no user-task UI in this process).

---

## Integration Tests — `InsuranceClaimIT.java`

Three test methods, all extend from a common Testcontainers + Spring Boot
setup:

```
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
class InsuranceClaimIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
```

### Test 1 — happy path: small collision claim, settled

```
Start process: claimNumber="CLM-001", policyNumber="POL-42",
  claimType="collision", estimatedAmount=800.0, documentDeadline="P14D"

Correlate message "documentsReceived" on businessKey "CLM-001"

Await: process instance ended in "Claim settled"
Assert: settlementDecision == "approve"
Assert: approvedAmount == 720.0  // 800 * 0.9 = 720, <= 1000 → full appraised
```

### Test 2 — reject path: flood claim regardless of amount

```
Start process: claimNumber="CLM-002", policyNumber="POL-43",
  claimType="flood", estimatedAmount=5000.0, documentDeadline="P14D"

Correlate message "documentsReceived" on businessKey "CLM-002"

Await: process instance ended in "Claim rejected"
Assert: settlementDecision == "reject"
Assert: approvedAmount == 0.0
```

### Test 3 — timeout path: no documents received

```
Start process: claimNumber="CLM-003", policyNumber="POL-44",
  claimType="collision", estimatedAmount=500.0, documentDeadline="PT3S"

// Do NOT send the message

Await (up to 30s): process instance ended in "Claim closed — documents not received"
```

All tests use Awaitility, no `Thread.sleep`.

Message correlation: `runtimeService.createMessageCorrelation("documentsReceived").processInstanceBusinessKey(claimNumber).correlate()`.

---

## README

Eight sections per EXAMPLE_STANDARDS.md §8:

1. Title + concept: **event-based gateway** + parallel gateway as secondary.
2. What you will learn: event-based gateway race (message vs timer),
   parallel AND-split/join for concurrent service calls,
   DMN FIRST hit policy with FEEL outputs,
   message correlation by business key.
3. Process model PNG (`src/main/resources/insurance-claim.png`).
4. Prerequisites: JDK 21, Docker.
5. Run it: `docker compose up -d` → `./mvnw spring-boot:run`; Cockpit at
   `http://localhost:8080`, `demo/demo`.
6. Walk-through: submit a claim via curl, correlate document message, observe settlement; repeat without message to see timeout branch.
7. How it works: short prose linking BPMN elements → delegate beans.
8. Run the tests: `./mvnw verify` / `./gradlew build`.

---

## README Concept Mapping Table (root `README.md`)

Add a new **BPMN Concept Reference** section to the operaton-examples root
`README.md`, after the existing examples table. Three sub-tables:

### BPMN Concepts

Derived from a scan of all example BPMN files. Each row lists the concept,
the example(s) that primarily demonstrate it, and a brief note.

| BPMN Concept | Example(s) | Notes |
|---|---|---|
| Service task | getting-started, service-tasks | Basic Java delegate and Spring bean |
| User task | user-task-forms | Forms, candidate groups |
| Script task | service-tasks | JavaScript / Groovy inline |
| Business rule task (DMN) | dmn-decision, insurance-claim | FEEL, hit policies |
| Send / receive task | — | — |
| Exclusive gateway (XOR) | getting-started, dmn-decision | Default flow, condition expressions |
| **Parallel gateway (AND)** | **insurance-claim** | AND-split / AND-join, concurrent branches |
| **Event-based gateway** | **insurance-claim** | Race between message and timer |
| Inclusive gateway (OR) | inclusive-gateway | — |
| Message start event | message-events | — |
| Timer start event | timer-events | Cron, cycle, duration |
| Message intermediate catch | message-events, insurance-claim | Correlation by business key |
| Timer intermediate catch | timer-events, insurance-claim | ISO-8601 duration variable |
| Signal intermediate catch/throw | signal-events | — |
| Error boundary event | error-compensation | Non-interrupting and interrupting |
| Compensation | error-compensation | Compensation boundary + handler |
| Multi-instance | multi-instance | Sequential and parallel |
| Call activity | call-activity | Sub-process reuse |
| Event sub-process | event-subprocess | Error and message-triggered |
| External task | external-task-worker | Worker API |
| Async continuation | async-continuation | Exclusive job lock |

### Integrations

| Integration | Example(s) |
|---|---|
| REST (Spring WebClient) | integration-rest |
| Mail (Jakarta Mail) | integration-mail |
| Kafka | integration-kafka |
| Operaton Connectors | integration-connectors |
| Micrometer / Prometheus | approval-sla-metrics |

### Platforms / Runtimes

| Platform | Example(s) |
|---|---|
| Spring Boot (embedded) | All use-cases, getting-started, …, approval-sla-metrics |
| Quarkus (embedded) | runtime-quarkus |
| Tomcat (shared engine) | distribution-tomcat |
| WildFly (shared engine) | distribution-wildfly |
| Flowset Control + SSO | operaton-flowset-sso (operaton-example-projects) |

---

## Out of Scope

- No Tasklist user tasks — this is a pure service/automated flow.
- No additional users or groups seeded.
- No Grafana/Prometheus stack.
- The "fraud check" and "appraiser" are simple in-process delegates, not
  external services — the example's point is the gateway topology, not
  the integrations.
