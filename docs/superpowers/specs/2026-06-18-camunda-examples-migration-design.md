# Design: Migrate Camunda BPM Examples

**Date:** 2026-06-18  
**Status:** Approved

## Overview

Migrate seven Camunda BPM examples into the `operaton-examples` repository, adopting all
standards defined in `docs/EXAMPLE_STANDARDS.md`. Where a source example overlaps with an
existing example, enrich the existing one rather than duplicating it.

## Source examples and their fate

| Camunda source | Action | Target |
|---|---|---|
| `usertask/task-form-external-jsf` | **Skip** — deprecated; source repo is README-only |
| `usertask/task-form-embedded-json-variables` | **Enrich** existing | `27-spin-json` |
| `multi-tenancy/tenant-identifier-embedded` | **Enrich** existing | `28-multi-tenancy` |
| `multi-tenancy/tenant-identifier-shared` | **Enrich** existing | `28-multi-tenancy` |
| `multi-tenancy/schema-isolation` | **Skip** — near-complete rewrite required; concept well outside current scope |
| `scripttask/xslt-scripttask` | **Port** as new example | `22-xslt-script-task` |
| `deployment/spring-wildfly-non-pa` | **Integrate** into existing | `21-distribution-wildfly` |

---

## Change 1 — New example `22-xslt-script-task`

### Concept

Groovy script tasks performing XSLT transformation on XML process variables using Java's
built-in `javax.xml.transform` API. No external script engine extension required.

The Camunda original used `org.camunda.community.template.engine:camunda-7-template-engine-xslt`,
which has no Operaton equivalent. The adaptation uses `scriptFormat="groovy"` and calls
`TransformerFactory.newInstance()` directly — equivalent result, zero added dependency.

### Ordinal

`22` — next free ordinal per `EXAMPLE_STANDARDS.md §2`.

### Directory

`examples/22-xslt-script-task/`  
Package: `org.operaton.examples.xsltscripttask`  
Maven artifactId: `xslt-script-task`

### Process model — `xslt-script-task.bpmn`

```
Start Event "Input XML received"
  → Script Task "Transform XML"  (scriptFormat="groovy", operaton:resource="deployment://transform.groovy")
      ↓ boundary error event "transform-error"
  → End Event "XML transformed"          End Event "Transformation failed"
```

Process ID: `xslt-script-task`  
`operaton:historyTimeToLive="P30D"`

Variable contract:
- **Input** `inputXml` (String) — XML document set at process start
- **Output** `transformedXml` (String) — XSLT result written by the script

### Key source files

| Path | Purpose |
|---|---|
| `src/main/resources/xslt-script-task.bpmn` | Process definition |
| `src/main/resources/org/operaton/examples/xsltscripttask/transform.groovy` | Groovy script; reads `inputXml` from `execution`, applies XSLT, writes `transformedXml` |
| `src/main/resources/org/operaton/examples/xsltscripttask/transform.xsl` | XSLT stylesheet; transforms `<order>` into `<invoice>` shape (same concept as Camunda original) |
| `src/main/resources/org/operaton/examples/xsltscripttask/sample-input.xml` | Sample XML committed as test fixture |
| `src/main/java/org/operaton/examples/xsltscripttask/XsltScriptTaskApplication.java` | `@SpringBootApplication` |
| `src/test/java/org/operaton/examples/xsltscripttask/XsltScriptTaskIT.java` | Integration test |

### Integration test

`XsltScriptTaskIT`:
- Testcontainers PostgreSQL via `@ServiceConnection`
- Starts process with `inputXml` = `sample-input.xml` contents
- Asserts `transformedXml` variable contains expected root element and key content (XMLUnit or string assertion)
- Happy path: valid `<order>` XML → asserts `<invoice>` root in result
- Error path: process started with a non-XML string as `inputXml`; the Groovy script catches `TransformerException` and calls `execution.createBpmnError("transform-error", …)`; test asserts the process ends via the error boundary or error end event (a boundary error event on the script task catches the thrown BpmnError)

### Infrastructure

- `docker-compose.yml`: PostgreSQL 16-alpine, port 5432, healthcheck
- Dual build: `./mvnw verify` (failsafe) and `./gradlew build` both pass

---

## Change 2 — Enrich `27-spin-json` with embedded forms

### What changes

The existing example is fully automated (Start → Validate → Prepare Offer → End). The enrichment
adds a Tasklist-facing embedded form at the start and a review task at the end, showing the
complete JSON-variable round-trip through forms.

### BPMN changes to `loan-application.bpmn`

1. Start event: add `operaton:formKey="embedded:app:forms/start-application.html"`
2. New user task **"Review offer"** inserted between `ServiceTask_PrepareOffer` and
   `EndEvent_OfferReady`:
   - `operaton:candidateGroups="loan-officers"`
   - `operaton:formKey="embedded:app:forms/review-offer.html"`

### New files

| Path | Purpose |
|---|---|
| `src/main/webapp/forms/start-application.html` | AngularJS embedded form; declares `application` as `type: 'json'` via `camForm.variableManager.createVariable`; binds `applicantName`, `amount`, `termMonths` fields |
| `src/main/webapp/forms/review-offer.html` | Read-only form; fetches `annualInterestRate` and `monthlyPayment` variables for display |

### Changed files

| Path | Change |
|---|---|
| `src/main/resources/loan-application.bpmn` | Add form keys; add Review offer user task + sequence flows; re-render PNG |
| `src/main/java/org/operaton/examples/spinjson/DataInitializer.java` | Add `loan-officers` group; seed user `carol` as loan officer |
| `src/test/java/org/operaton/examples/spinjson/LoanApplicationIT.java` | After `PrepareOffer` completes, query the Review offer task, complete it as `carol`; assert `annualInterestRate` and `monthlyPayment` are present |

Existing delegates (`ValidateApplicationDelegate`, `PrepareOfferDelegate`) and `LoanApplication`
POJO are **unchanged**.

---

## Change 3 — Enrich `28-multi-tenancy` (README only)

Both camunda source examples (`tenant-identifier-embedded`, `tenant-identifier-shared`) cover
concepts already fully demonstrated by the existing `ContractReviewIT`:
tenant-specific deployments, `getTenantId()` in delegates, `tenantIdIn()` task queries, and
end-to-end isolation tests.

**Change:** Add a "Multi-tenancy approaches" section to `README.md` that explains:
- **Tenant-identifier** (what this example shows): shared schema and engine; rows tagged by
  `tenantId`; low overhead; suitable when tenants trust the application layer for isolation
- **Schema isolation** (the alternative): one PostgreSQL schema per tenant; one process engine
  per tenant; stronger isolation; higher resource cost — with a reference to
  `camunda-bpm-examples/multi-tenancy/schema-isolation` for readers who need it

No Java or BPMN changes.

---

## Change 4 — Extend `21-distribution-wildfly` with non-PA Spring variant

### Concept

A plain Spring web application can connect to the WildFly-managed shared process engine via
JNDI without becoming a `ProcessApplication`. This pattern is useful when the application
wants Spring DI but does not own the process deployment lifecycle.

### Additions — no changes to existing files

**New Java files:**

| Path | Purpose |
|---|---|
| `src/main/java/org/operaton/examples/distributionwildfly/client/ProcessEngineClient.java` | `@Component`; injected with `ProcessEngine`; exposes `listDeployments()` calling `repositoryService.createDeploymentQuery().list()` |
| `src/main/java/org/operaton/examples/distributionwildfly/client/SpringWildflyConfig.java` | `@Configuration`; `ProcessEngine` bean via `JndiObjectFactoryBean` looking up `java:global/operaton-engine` |

**New config files:**

| Path | Purpose |
|---|---|
| `src/main/webapp/WEB-INF/applicationContext.xml` | Spring XML context; `<jee:jndi-lookup>` as alternative to the `@Configuration` class |
| `src/main/webapp/WEB-INF/web.xml` | `<resource-ref>` for engine JNDI name; `ContextLoaderListener` |

**README:** New section "Variant: Non-ProcessApplication Spring client" explaining the pattern,
when to use it, and pointing to the four new files.

**IT:** No new IT assertion for the Spring client — the `ProcessEngineClient` bean only activates
inside a real WildFly JVM and cannot be driven from the Testcontainers REST-based IT. The existing
`DocumentApprovalIT` already proves the WAR deploys and the engine functions correctly. The Spring
client is verified by manual smoke test: `docker compose up`, deploy the WAR, hit the `/deployments`
REST endpoint exposed by `ProcessEngineClient` via a JAX-RS or Spring MVC resource, and verify the
response lists the deployed process. This is documented in the README walk-through.

---

## Acceptance criteria (from `EXAMPLE_STANDARDS.md §10`)

For each deliverable:

- [ ] `./mvnw verify` passes (failsafe ran > 0 ITs)
- [ ] `./gradlew build` passes
- [ ] `docker compose up -d && ./mvnw spring-boot:run` works where applicable
- [ ] BPMN/DMN use `operaton` namespace, have DI, names, `historyTimeToLive`
- [ ] ITs use Testcontainers PostgreSQL, no H2, no `Thread.sleep`
- [ ] Happy path + alternative path tested end-to-end
- [ ] README has all 8 sections; PNG rendered via `render-bpmn.sh` and referenced
- [ ] Versions match `pom.xml` == `build.gradle.kts` == root README table
- [ ] `§7` conventions: `demo/demo` admin, named seed users, `application.yaml`
- [ ] No dead code, unused deps, TODO stubs
- [ ] `grep -r camunda` result is empty for all new/changed files

## Pinned versions (from root README)

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Operaton | 2.1.1 |
| PostgreSQL | 16 |
