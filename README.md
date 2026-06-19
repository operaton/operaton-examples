# Operaton Examples

A curated catalog of minimal, production-quality example projects for
[Operaton](https://operaton.org) — the open-source BPMN process engine.
Every example is self-contained, builds with **both** Maven Wrapper and
Gradle Wrapper, ships a Docker Compose setup for local exploration, and is
verified end-to-end by **Testcontainers** integration tests: building an
example means testing its processes against real integrations.

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 |
| Docker | any recent version (required for tests and local run) |
| Distribution images (`operaton/tomcat`, `operaton/wildfly`, `operaton/operaton`) | `2.1.1` |

Pinned stack (all examples): Spring Boot **4.1.0**, Operaton **2.1.1**,
Maven Wrapper **3.9.12**, Gradle Wrapper **9.2.0**, PostgreSQL **16**.

## Using an example

```bash
cd examples/getting-started
docker compose up -d --wait # start PostgreSQL (and example-specific services)
./mvnw spring-boot:run      # or: ./gradlew bootRun
# Cockpit/Tasklist: http://localhost:8080  (demo/demo)
./mvnw verify               # or: ./gradlew build — runs Testcontainers ITs
```

## Catalog

### Core Examples

| Example | Demonstrates |
|---|---|
| [getting-started](examples/getting-started) | Embedded engine, service task delegate, user task, exclusive gateway |
| [service-tasks](examples/service-tasks) | Java delegates, expression delegates, BpmnError, job retry |
| [external-task-worker](examples/external-task-worker) | External task pattern, long-polling worker, topic subscription |
| [user-task-forms](examples/user-task-forms) | User tasks, embedded forms, task lifecycle, form variables |
| [dmn-decision](examples/dmn-decision) | DMN decision tables, DRD, decision evaluation, business rule task |
| [message-events](examples/message-events) | Message start event, intermediate message catch, business-key correlation |
| [timer-events](examples/timer-events) | Timer boundary event (SLA escalation), job executor API, testing timers |
| [error-compensation](examples/error-compensation) | BPMN compensation (saga pattern), compensation handlers, BpmnError trigger |
| [multi-instance](examples/multi-instance) | Parallel multi-instance user tasks, collection loop, completion condition |
| [integration-rest](examples/integration-rest) | REST delegate via RestTemplate, 4xx→BpmnError, WireMock Testcontainers |
| [integration-mail](examples/integration-mail) | Spring Mail in delegates, Mailpit Testcontainers for SMTP + REST assertions |
| [integration-kafka](examples/integration-kafka) | Kafka listener starts process, delegate publishes result, Awaitility assertions |
| [call-activity](examples/call-activity) | Process composition via call activity, variable in/out mappings, child process history |
| [signal-events](examples/signal-events) | Signal broadcast vs. message point-to-point, intermediate catch/throw, multi-subscriber |
| [event-subprocess](examples/event-subprocess) | Non-interrupting signal audit subprocess, interrupting error handler subprocess |
| [inclusive-gateway](examples/inclusive-gateway) | Inclusive (OR) gateway — multiple concurrent paths, join waits for all active tokens |
| [async-continuation](examples/async-continuation) | asyncBefore transaction boundaries, manual job execution, failedJobRetryTimeCycle |

### Advanced Engine Features

| Example | Demonstrates |
|---|---|
| [engine-plugin](examples/engine-plugin) | Custom `AbstractProcessEnginePlugin` with `BpmnParseListener` injecting `TaskListener` for audit logging |
| [job-retry-profile](examples/job-retry-profile) | `failedJobRetryTimeCycle`, custom retry profiles, observing and controlling retry counts |
| [command-interceptor](examples/command-interceptor) | `CommandInterceptor` wrapping all engine API calls for auditing (name + duration) |
| [process-migration](examples/process-migration) | `MigrationPlan` API to migrate running instances between process definition versions |
| [spin-json](examples/spin-json) | JSON-typed process variables via Operaton Spin — store and retrieve Java objects as JSON |
| [xslt-script-task](examples/xslt-script-task) | XSLT transformation in a Groovy script task using javax.xml.transform |
| [multi-tenancy](examples/multi-tenancy) | Tenant-identifier multi-tenancy: shared engine, isolated deployments and instances per tenant |
| [bpmn-model-api-parse](examples/bpmn-model-api-parse) | Programmatic inspection of deployed BPMN models using the BPMN Model API |
| [bpmn-model-api-generate](examples/bpmn-model-api-generate) | Create and deploy BPMN processes programmatically without XML authoring |
| [unit-testing](examples/unit-testing) | Fast isolated unit tests with `ProcessEngineExtension` + H2, complemented by Testcontainers ITs |

### Platform Integration

| Example | Demonstrates |
|---|---|
| [integration-connectors](examples/integration-connectors) | HTTP connector (declarative, no Java delegate) + custom Connector SPI |
| [runtime-quarkus](examples/runtime-quarkus) | Embedded engine in Quarkus/CDI (no Spring Boot) |
| [distribution-tomcat](examples/distribution-tomcat) | Process-application WAR deployed into `operaton/tomcat` shared-engine container |
| [distribution-wildfly](examples/distribution-wildfly) | Process-application WAR deployed into `operaton/wildfly` shared-engine container |

### Use Cases

| Use Case | Process | Demonstrates |
|---|---|---|
| [leave-request](examples/use-cases/leave-request) | Employee leave approval | Timer escalation (non-interrupting), VacationBalanceService, SQL schema init |
| [loan-application](examples/use-cases/loan-application) | Loan origination | REST credit scoring, DMN risk assessment, Spring Mail notifications |
| [incident-management](examples/use-cases/incident-management) | IT support ticket | Signal escalation boundary, timer SLA boundary, 4-swimlane BPMN, REST integration |
| [order-fulfillment](examples/use-cases/order-fulfillment) | E-commerce order | Error boundary on payment, async continuation, compensation, WireMock inventory/payment stubs |
| [candidate-screening](examples/use-cases/candidate-screening) | AI recruiting screening | LLM scoring + email generation via HTTP connector, LLM-driven confidence gateway, human-in-the-loop on borderline, calendar slot query, WireMock IT |

## Anatomy of every example

```mermaid
flowchart LR
    subgraph example [examples/name]
        BPMN["BPMN/DMN models<br/>(operaton namespace, full DI)"]
        APP["Spring Boot app<br/>(delegates, config)"]
        IT["Integration tests<br/>(Testcontainers)"]
        DC["docker-compose.yml<br/>(local exploration)"]
        RM["README<br/>(Mermaid diagrams, walkthrough)"]
    end
    BPMN --> APP
    APP --> IT
    IT -->|"PostgreSQL + real<br/>external systems"| TC[(containers)]
    DC -->|same services| TC
```

## Quality bar

Every example satisfies [docs/EXAMPLE_STANDARDS.md](docs/EXAMPLE_STANDARDS.md)
— the definition of done covering modeling, testing, documentation and dual
builds. CI builds every example with both build systems on every push.

## Contributing (humans and AI agents)

AI agents: start with [AGENTS.md](AGENTS.md).
Humans: same rules — see the review checklist in
[docs/EXAMPLE_STANDARDS.md](docs/EXAMPLE_STANDARDS.md#10-review-checklist-copy-into-every-example-pr).

## License

[Apache-2.0](LICENSE)
