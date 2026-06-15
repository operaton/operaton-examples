# Platform Integration Examples — Design Spec

**Date:** 2026-06-15  
**Status:** Approved  
**Scope:** Five new examples (18–22) demonstrating alternative runtimes and deployment models for the Operaton engine.

---

## 1. Motivation

The existing catalog (01–17 + UC-01–UC-04) demonstrates all major BPMN/engine concepts against a single deployment shape: embedded engine in a Spring Boot 4 application. This spec adds a **Platform Integration** group showing how the same engine runs on other runtimes and deployment topologies that users encounter in practice.

---

## 2. New Catalog Entries

All five live under `examples/` as ordinals 18–22, auto-discovered by CI.

A new **Platform Integration** subsection is added to the root `README.md` catalog table (below the existing Examples table, above Use Cases).

| # | Dir | Name | Primary concept |
|---|---|---|---|
| 18 | `18-integration-connectors` | Integration — Connectors | HTTP connector (declarative REST call, no Java delegate) + custom Connector SPI |
| 19 | `19-runtime-quarkus` | Runtime — Quarkus | Embedded engine on Quarkus/CDI instead of Spring Boot |
| 20 | `20-distribution-tomcat` | Distribution — Tomcat | Process-application WAR deployed into `operaton/tomcat` shared-engine container |
| 21 | `21-distribution-wildfly` | Distribution — Wildfly | Process-application WAR deployed into `operaton/wildfly` shared-engine container |
| 22 | `22-operations-flowset-control` | Operations — Flowset Control | `operaton/operaton` with built-in webapps disabled; Flowset Control as external ops UI |

---

## 3. Standards Extension — §11 Platform Integration Examples

`docs/EXAMPLE_STANDARDS.md` gets a new binding section §11. The following table maps each shape to which base rules apply, which are relaxed, and what replaces them.

### 3.1 Shape A — Embedded, non-Spring-Boot (example 19)

Applies to: `19-runtime-quarkus`

| Base rule | Status | Replacement / note |
|---|---|---|
| §3 Dual build Maven + Gradle | **Applies** — use Quarkus Maven plugin + Quarkus Gradle plugin | — |
| §7 Spring Boot `@SpringBootApplication` | **Waived** | Quarkus `@QuarkusMain` or implicit; `application.properties` (not YAML) |
| §7 `demo/demo` Cockpit admin user | **Waived** — no Cockpit webapp | Engine user seeded via Quarkus-compatible init; REST API verified instead |
| §5 Testcontainers IT | **Applies** | `@QuarkusTest` + `QuarkusTestResourceLifecycleManager` managing the Postgres container |
| All other §1–§9 rules | **Apply unchanged** | — |

### 3.2 Shape B — Process-application WAR / shared engine (examples 20, 21)

Applies to: `20-distribution-tomcat`, `21-distribution-wildfly`

| Base rule | Status | Replacement / note |
|---|---|---|
| §3 Dual build Maven + Gradle | **Applies** — `war` packaging in both; `maven-failsafe-plugin` + Gradle `test` task | — |
| §7 Spring Boot | **Waived** | `ProcessApplication` API (`ServletProcessApplication`); `META-INF/processes.xml` |
| §7 `application.yaml` | **Waived** | `processes.xml` + optional engine-local `bpm-platform.xml` snippet | 
| §5 Testcontainers IT | **Applies — modified** | IT starts `operaton/tomcat` (or `-wildfly`) + Postgres via Testcontainers `GenericContainer`; copies the built WAR in; drives the process via engine REST API; asserts via REST/history |
| §7 `demo/demo` admin user | **Applies** — engine is pre-seeded in the container image | — |
| §6 docker-compose | **Applies** | Postgres + distribution container with `DB_*` env vars + WAR volume-mounted into deploy dir |
| Version pinning — library | Not applicable | Pin the **distribution image tag** (separate row in root README version table; may lag embedded library version) |
| All other §1–§4, §8–§9 rules | **Apply unchanged** | — |

### 3.3 Shape C — Container-only / no application JAR (example 22)

Applies to: `22-operations-flowset-control`

| Base rule | Status | Replacement / note |
|---|---|---|
| §2 Project structure | **Modified** | No `src/main/java`; project is a **test-only module** (failsafe/Gradle `test`) with a docker-compose and a sample BPMN mounted into the engine |
| §3 Dual build | **Applies** — both wrappers run the smoke IT | — |
| §5 Testcontainers IT — full e2e | **Reduced to smoke IT** | Assert: (a) engine REST `/engine-rest/engine` → HTTP 200; (b) `/operaton/app/cockpit`, `/tasklist`, `/admin` → 404; (c) Flowset Control container healthy. The Control→engine connection is manual (UI-driven, documented in README). |
| §7 app conventions | **Waived** | No application code; no `@SpringBootApplication` |
| §6 docker-compose | **Applies** | Postgres (engine) + `operaton/operaton` (webapps disabled) + Flowset Control own Postgres + flowset-control service |
| All other §8–§9 rules | **Apply unchanged** | README has all 8 sections; Mermaid shows the container topology not a BPMN flowchart |

### 3.4 Version table row

The root `README.md` version table gains a **Distribution images** row:

```
| Operaton distribution images | operaton/tomcat, operaton/wildfly, operaton/operaton | latest stable release tag (see example README) |
```

This row is separate from the embedded library version and may differ from it; the discrepancy is explained in a README note.

---

## 4. Per-Example Design

### 4.1 Example 18 — `18-integration-connectors`

**Primary concept:** Declarative service integration with `operaton:connector` — no Java delegate — plus a custom `Connector` SPI implementation.

**Runtime shape:** Embedded Spring Boot 4 (standard, fully compliant with §1–§9).

**BPMN process — `currency-conversion.bpmn`:**

```
Start → [Service Task: Fetch exchange rate]
          ↓ (success)
     → [Service Task: Convert amount]
          ↓
     → [Exclusive GW: rate valid?]
          ↓ yes          ↓ no (404 / BpmnError)
     → End (success)  → End (rate-unavailable)
```

- *Fetch exchange rate*: `operaton:connector`, `connectorId="http-connector"`. Input mappings: `url`, `method=GET`, `headers`. Output mapping: parse JSON response body into `exchangeRate` variable. On HTTP 4xx, a connector error maps to a `BpmnError` via boundary event.
- *Convert amount*: `operaton:connector`, `connectorId="currency-convert"`. Custom connector: takes `amount` + `exchangeRate` inputs, outputs `convertedAmount`. Pure arithmetic, no network.

**Custom connector implementation:**
- `CurrencyConvertConnector extends AbstractConnector<CurrencyConvertRequest, CurrencyConvertResponse>` with matching request/response types.
- Registered via `META-INF/services/org.operaton.connect.spi.Connector`.
- Picked up automatically by Operaton's `Connectors` factory on startup.

**Dependencies (additions to standard set):**
- `operaton-connect-http-client`
- `wiremock-testcontainers-module` (test)

**Integration test `CurrencyConversionIT`:**
- Postgres + WireMock Testcontainers.
- Happy path: WireMock returns valid rate JSON → assert `convertedAmount` variable + process `COMPLETED`.
- Alt path: WireMock returns 404 → assert process ends at `rate-unavailable` end event.
- Awaitility for any async continuations; no `Thread.sleep`.

**docker-compose:** Postgres only (WireMock is Testcontainers-only; the app calls a configurable URL).

---

### 4.2 Example 19 — `19-runtime-quarkus`

**Primary concept:** Embedded Operaton engine running inside a Quarkus CDI application instead of Spring Boot.

**Runtime shape:** Embedded Quarkus (Shape A, §11.1).

**App:**
- `QuarkusApplication` (or rely on Quarkus auto-start).
- `operaton-bpm-quarkus-engine` BOM-managed via Quarkus BOM.
- One simple process `order-approval.bpmn`: Start → [Service Task: Validate order] → [Exclusive GW: approved?] → End(approved) / End(rejected).
- Delegate `ValidateOrderDelegate` is a `@Named` CDI bean; resolved via `${validateOrderDelegate}`.
- `application.properties` datasource + `%test.quarkus.datasource.jdbc.url` overridden by Testcontainers.

**Test `OrderApprovalIT`:**
- `@QuarkusTest`.
- `PostgreSQLQuarkusTestResource implements QuarkusTestResourceLifecycleManager` — starts Testcontainers Postgres, injects `QUARKUS_DATASOURCE_JDBC_URL` system property before Quarkus boots.
- Inject `ProcessEngine` (CDI bean) or use the REST API; start process, drive gateway, assert history.
- Happy path (approved) + alt path (rejected).

**Dual build:**
- Maven: `quarkus-maven-plugin` + `maven-failsafe-plugin`.
- Gradle: `io.quarkus` Gradle plugin + standard `test` task.

**docker-compose:** Postgres only (app is `./gradlew quarkusDev` / `./mvnw quarkus:dev`).

---

### 4.3 Example 20 — `20-distribution-tomcat`

**Primary concept:** Process-application WAR deployed into a pre-built `operaton/tomcat` container — the *shared-engine* deployment model.

**Runtime shape:** Shape B (§11.2).

**Artifact — WAR:**
- `war` packaging.
- `META-INF/processes.xml` (deploy-on-boot, default engine).
- `DocumentApprovalApplication extends ServletProcessApplication` (annotated `@ProcessApplication`).
- `document-approval.bpmn`: Start → [Service Task: Check document] → [Exclusive GW: approved?] → End(approved) / End(rejected).
- `CheckDocumentDelegate implements JavaDelegate` — CDI bean.
- **No Spring Boot, no `application.yaml`.** Standard Servlet 6 / CDI 4.

**Integration test `DocumentApprovalIT`:**
- `@Testcontainers`.
- `PostgreSQLContainer` for engine DB.
- `GenericContainer("operaton/tomcat:<tag>")` configured with `DB_DRIVER=postgresql`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `WAIT_FOR=postgres:5432`.
- WAR copied into `/operaton/server/apache-tomcat-*/webapps/` via `withCopyFileToContainer` (path from `mvn package` / `gradle war` output).
- `waitingFor(Wait.forHttp("/engine-rest/engine"))`.
- Drive process via `RestAssured` against the engine REST endpoint.
- Happy path + alt path, both via REST + history API.
- IT bound to `post-integration-test` / runs after `package` in both build systems.

**docker-compose:** Postgres + `operaton/tomcat` with `DB_*`, `WAIT_FOR`, and WAR bind-mounted from `./target/` (or `./build/libs/`).

---

### 4.4 Example 21 — `21-distribution-wildfly`

**Primary concept:** Same process-application WAR deployed into `operaton/wildfly` — demonstrates the Jakarta EE app-server deployment model and highlights the differences from Tomcat.

**Runtime shape:** Shape B (§11.2). Identical structure to example 20 except:

- Container image: `operaton/wildfly:<tag>`.
- Deployment directory inside container: `/opt/jboss/wildfly/standalone/deployments/`.
- `WAIT_FOR` points to Postgres before Wildfly starts.
- `waitingFor` health check uses the Wildfly management port (`9990`) or engine REST endpoint.
- README explicitly cross-references example 20 and calls out what changed (Jakarta EE datasource binding, JNDI, deployment scanner vs Tomcat's `webapps/`).
- Same BPMN and delegates as example 20 (shared teaching process, different container focus).

---

### 4.5 Example 22 — `22-operations-flowset-control`

**Primary concept:** Running `operaton/operaton` with its built-in webapps (Cockpit, Tasklist, Admin) disabled, and using Flowset Control Community as an external operations UI over REST.

**Runtime shape:** Shape C (§11.3) — no application JAR, test-only module.

**docker-compose services:**

```yaml
services:
  postgres-engine:        # PostgreSQL for the engine
  operaton:               # operaton/operaton:<tag>
    environment:
      DB_DRIVER: postgresql
      DB_URL: jdbc:postgresql://postgres-engine:5432/operaton
      ...
    # Built-in webapps disabled: mount empty dir over /operaton/webapps
    # OR start with --rest flag (self-contained distribution supports this)
    volumes:
      - ./webapps-disabled:/operaton/webapps   # empty dir
    # Sample process BPMN auto-deployed via /operaton/configuration/resources/
    volumes:
      - ./resources:/operaton/configuration/resources
  postgres-flowset:       # PostgreSQL for Flowset Control's own data
  flowset-control:        # flowset/flowset-control-community:<tag>
    ports:
      - "8081:8081"
    depends_on:
      operaton: { condition: service_healthy }
      postgres-flowset: { condition: service_healthy }
```

**Sample process** (`hello-world.bpmn`): Start → [Service Task: Log greeting] → End. Deployed automatically from `/operaton/configuration/resources/` on engine boot. Gives Flowset Control something to display.

**Smoke IT (`OperationsIT`):**
1. Assert `GET /engine-rest/engine` → HTTP 200 (engine REST up).
2. Assert `GET /operaton/app/cockpit/default/` → HTTP 404 (Cockpit disabled).
3. Assert `GET /operaton/app/tasklist/default/` → HTTP 404 (Tasklist disabled).
4. Assert `GET /operaton/app/admin/default/` → HTTP 404 (Admin disabled).
5. Assert Flowset Control container healthy (port 8081 responds).

The IT uses `DockerComposeContainer` or individual `GenericContainer`s pointing at each other on a `Network.newNetwork()`. Testcontainers manages all containers; no `Thread.sleep`.

**README walk-through** includes the manual step to connect Flowset Control to the engine REST endpoint (navigate to BPM Engines, enter `http://operaton:8080/engine-rest`).

---

## 5. Files changed outside the new examples

| File | Change |
|---|---|
| `docs/EXAMPLE_STANDARDS.md` | Add §11 (verbatim from §3 of this spec) |
| `README.md` | Add Platform Integration catalog subsection (examples 18–22) + distribution image row in version table |
| `.operaton-starter.yml` | Add five new entries under a `platform-integrations` section |
| `pom.xml` (root aggregator) | Add five new `<module>` entries |
| `settings.gradle.kts` (root) | Add five `include` directives |

---

## 6. Ordinal assignment

Next free ordinal after existing 17 examples is 18. Assign sequentially 18–22. These are permanent — never renumber.

---

## 7. Open questions (resolved)

| Question | Decision |
|---|---|
| Single spec or one per example? | Single combined spec (this document) |
| Distribution: Tomcat, Wildfly, or both? | Both (20 = Tomcat, 21 = Wildfly) |
| Connector: HTTP only or HTTP + custom SPI? | HTTP + custom connector SPI |
| Distribution image version vs embedded version mismatch | Documented divergence; separate row in README version table |
| Flowset Control connection (automated vs manual) | Manual (UI-driven); IT only smoke-tests containers up + webapps absent |
