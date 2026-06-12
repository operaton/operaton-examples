# Example Standards — Definition of Done

Every example in this repository MUST satisfy every item below. There are no
exceptions. An example that fails one checklist item is not mergeable.
These standards are derived from the operaton-starter use-case templates and
its BMAD quality gates ("100% compile, pass tests, start successfully").

All numbered sections are binding. The checklist in §10 is a working summary,
not a substitute — passing the checklist does not waive any section.

## 1. Scope

- One example demonstrates **one** primary Operaton concept (named in the
  README's first sentence). Secondary concepts are allowed only when required
  by the primary one.
- Minimal: no code, dependency, or model element that does not serve the
  demonstrated concept. If a class can be deleted and the example still
  demonstrates its concept, delete it.
- Self-contained: an example never depends on another example or on a shared
  parent module. Copy, don't share — examples are read in isolation.

## 2. Project structure

```
examples/NN-short-name/
├── mvnw, mvnw.cmd, .mvn/wrapper/          # Maven Wrapper (committed)
├── pom.xml
├── gradlew, gradlew.bat, gradle/wrapper/  # Gradle Wrapper (committed)
├── build.gradle.kts, settings.gradle.kts
├── docker-compose.yml                     # only the services this example needs
├── README.md
└── src/
    ├── main/java/io/github/kthoms/operaton/examples/<name>/
    ├── main/resources/                    # *.bpmn, *.dmn, application.yaml
    └── test/java/io/github/kthoms/operaton/examples/<name>/
```

- Directory name: `NN-kebab-case` where `NN` is a two-digit ordinal defining
  recommended reading order.
- Java package: `io.github.kthoms.operaton.examples.<name>` where `<name>` is
  the directory name without the ordinal, with hyphens removed
  (`01-getting-started` → `gettingstarted`).
- Maven coordinates: groupId `io.github.kthoms.operaton.examples`,
  artifactId = directory name without ordinal (`getting-started`),
  version `0.1.0-SNAPSHOT`.
- Ordinals are permanent: never renumber existing examples; a new example
  takes the next free ordinal even if it breaks thematic grouping.

## 3. Dual build — Maven AND Gradle

- `./mvnw verify` and `./gradlew build` MUST both succeed from a clean
  checkout with only JDK 21 and Docker installed.
- Both builds compile the same `src/` tree and run the same tests. Gradle's
  `test` task discovers `*IT` classes via the JUnit Platform regardless of
  name; Maven runs them ONLY through failsafe — therefore `pom.xml` MUST
  declare `maven-failsafe-plugin` with the `integration-test` and `verify`
  goals. A green `./mvnw verify` that executed zero ITs is a broken build:
  reviewers check failsafe's `Tests run:` count is > 0.
- Versions (Java, Spring Boot, Operaton) MUST be identical in `pom.xml` and
  `build.gradle.kts`, and MUST match the table in the root README — the single source of truth for pinned versions.
- Dependency management via BOMs only: `spring-boot-dependencies` /
  `SpringBootPlugin.BOM_COORDINATES` plus `org.operaton.bpm:operaton-bom`.
  Never pin a version that a BOM already manages.

## 4. BPMN / DMN models

- Executable semantics (`delegateExpression`, `candidateGroups`, form
  attributes, history TTL, …) use
  `xmlns:operaton="http://operaton.org/schema/1.0/bpmn"` — never the
  `camunda` namespace. Non-executable tool metadata (an `exporter`
  attribute, a modeler metadata namespace) is tolerated but must be removed
  when it references Camunda.
- Every process: `operaton:historyTimeToLive` set (default `P30D`),
  `isExecutable="true"`, process `id` in kebab-case matching the file name
  (`order-approval.bpmn` → id `order-approval`).
- Every element has a meaningful `name` (verb-object for tasks: "Calculate
  order total"). Sequence flows out of gateways are named with their
  condition ("total ≥ 1000", "otherwise").
- Diverging exclusive gateways: every non-default outgoing flow has a
  `conditionExpression`; exactly one default flow is marked. (Converging
  gateways carry no conditions and no default.)
- Models include full BPMN DI (`bpmndi:BPMNDiagram`) so they render in the
  Operaton Cockpit and bpmn.io — a model without diagram interchange is
  not "well modeled".
- User tasks use `operaton:candidateGroups` (not hard-coded assignees).
- Service tasks use `operaton:delegateExpression="${beanName}"` referencing a
  Spring bean (not `operaton:class`), unless the example demonstrates
  otherwise.
- DMN: decision `id` in kebab-case matching the file name; the hit policy is
  a deliberate choice explained in the README; decision requirements graphs
  with more than one decision include DMN DI.

## 5. Testing — Testcontainers, end-to-end

- Integration tests are named `*IT` and live in `src/test/java`.
- Every IT class runs against **PostgreSQL via Testcontainers**
  (`@Testcontainers` + `@Container` + `@ServiceConnection`). H2 is forbidden
  in integration tests — examples must prove they work on a real database.
- External systems the example integrates with (Kafka, Keycloak, mail, …)
  are ALSO started via Testcontainers in the IT — the test must exercise the
  real integration, not a mock of it. (WireMock is acceptable only when the
  example's concept is "call an arbitrary third-party REST API".)
- Tests execute the process end-to-end: deploy → start → drive through wait
  states → assert it ended in the expected end event, with expected variable
  values and expected side effects on integrated systems.
- Both happy path and at least one alternative/error path are tested.
- No `Thread.sleep` — use Awaitility for asynchronous continuations and the
  job executor.
- `./mvnw verify` runs the ITs via failsafe; `./gradlew build` runs them via
  the standard `test` task. Building an example IS testing it.

## 6. Docker Compose (local exploration)

- `docker-compose.yml` contains exactly the services needed to run the
  example locally (always PostgreSQL; plus the example's external systems).
- Every service has a `healthcheck`; dependent services use
  `depends_on: condition: service_healthy`.
- Fixed, documented host ports; credentials are throwaway dev values stated
  in the README.
- `docker compose up -d` followed by `./mvnw spring-boot:run` MUST work with
  zero manual configuration.
- Examples assume they run one at a time: PostgreSQL is always published on
  host port 5432 and the app on 8080; stop other examples' stacks
  (`docker compose down`) before starting a new one. Additional ports are
  documented in the README.

## 7. Application conventions

- Spring Boot 4, single `@SpringBootApplication` class named
  `<Name>Application`.
- `application.yaml` (not `.properties`); datasource points at the
  docker-compose PostgreSQL; an admin user `demo/demo` is configured via
  `operaton.bpm.admin-user` so Cockpit/Tasklist are never empty.
- Additional users/groups seeded idempotently (DataInitializer component or
  `data.sql`), using human names (`alice`, `bob`), never `user1`.
- Delegates are complete, runnable implementations — never stubs that log
  "TODO".

## 8. Documentation

Every example README contains, in this order:

1. **Title + one-sentence statement** of the demonstrated concept.
2. **What you will learn** — 3-5 bullets.
3. **Process model** — Mermaid `flowchart` mirroring the BPMN (and a Mermaid
   `sequenceDiagram` when systems interact). Every flow node (event, task,
   gateway) and every named sequence flow in the BPMN appears in the Mermaid
   diagram; constructs Mermaid cannot render (lanes, boundary-event
   attachment) are approximated and noted in prose.
4. **Prerequisites** — JDK 21, Docker; exact versions.
5. **Run it** — `docker compose up -d`, then both
   `./mvnw spring-boot:run` and `./gradlew bootRun`; URLs and credentials
   for Cockpit/Tasklist (http://localhost:8080, demo/demo).
6. **Walk through it** — numbered manual walkthrough (Tasklist clicks and/or
   `curl` commands) covering the happy path and one alternative path.
7. **How it works** — short prose linking model elements to code
   (file links, not code dumps).
8. **Run the tests** — `./mvnw verify` and `./gradlew build`, one sentence on
   what the ITs prove.

- Code comments only where the code cannot speak (e.g. why an async
  continuation is placed where it is).

## 9. Quality gate (CI)

- CI builds every example with BOTH wrappers on every push/PR; a red example
  blocks merge.
- Adding an example = adding its directory; CI discovers it automatically.

## 10. Review checklist (copy into every example PR)

```
- [ ] ./mvnw verify passes from clean checkout (failsafe ran > 0 ITs)
- [ ] ./gradlew build passes from clean checkout
- [ ] docker compose up -d && ./mvnw spring-boot:run works, Cockpit reachable
- [ ] BPMN/DMN use operaton namespace, have DI, names, historyTimeToLive
- [ ] ITs use Testcontainers (PostgreSQL + real integrations), no H2, no sleeps
- [ ] Happy path + alternative path tested end-to-end
- [ ] README has all 8 sections; Mermaid matches BPMN element-for-element
- [ ] Versions match pom.xml == build.gradle.kts == root README table
- [ ] §7 app conventions: demo/demo admin user, named seed users, application.yaml
- [ ] No dead code, no unused dependencies, no TODO/stub delegates
```
