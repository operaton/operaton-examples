# Candidate Screening (LLM in a Process) — Design

**Goal:** A use-case example that calls an LLM over REST via the Operaton HTTP
connector to both *generate text* (decision emails) and *drive a gateway
decision* (a candidate fit score), in a job-application-screening process that
escalates the uncertain score band to a human and queries the recruiter's
calendar for an interview slot.

**Demonstrated concept (README first sentence):** Using an LLM inside a BPMN
process — the engine orchestrates LLM REST calls through the HTTP connector to
score an application, decide on a confidence-banded gateway, generate
candidate- and recruiter-facing emails, and find an interview slot — with a
human-in-the-loop on the uncertain band.

**Classification:** Use case → `examples/use-cases/candidate-screening`.

## Coordinates & naming

| Item | Value |
|---|---|
| Directory | `examples/use-cases/candidate-screening` |
| Maven artifactId | `uc-05-candidate-screening` |
| Maven name | `Operaton Example: Candidate Screening` |
| Java package | `org.operaton.examples.candidatescreening` |
| Spring Boot app | `CandidateScreeningApplication` |
| Process id / file | `candidate-screening` / `candidate-screening.bpmn` |
| Version | `0.1.0-SNAPSHOT` |

## Global constraints (from EXAMPLE_STANDARDS.md)

- Spring Boot **4.1.0**, Operaton **2.1.1**, Java **21** — identical in
  `pom.xml`, `build.gradle.kts`, and matching the root README version table.
- Dependency management via BOMs only (`spring-boot-dependencies` /
  `SpringBootPlugin.BOM_COORDINATES` + `org.operaton.bpm:operaton-bom`). Never
  pin a version a BOM manages.
- Dual build: `./mvnw verify` (failsafe runs `*IT`, count > 0) **and**
  `./gradlew build` both green from a clean checkout (JDK 21 + Docker only).
- BPMN uses `xmlns:operaton="http://operaton.org/schema/1.0/bpmn"`;
  `isExecutable="true"`; `operaton:historyTimeToLive="P30D"`; full BPMN DI;
  named elements and gateway flows; non-default exclusive-gateway flows carry
  `conditionExpression`, exactly one default.
- ITs use Testcontainers PostgreSQL (`@ServiceConnection`), no H2, no
  `Thread.sleep`. WireMock is permitted here because the concept *is* "call an
  arbitrary third-party REST API" (§5).
- `application.yaml`; `demo/demo` admin via `operaton.bpm.admin-user`; named
  seed users; complete runnable delegates/beans (no stubs).
- README has all 8 standard sections; PNG rendered via `scripts/render-bpmn.sh`
  and referenced; PNG registered in `.operaton-starter.yml` under `screenshots`.
- CI Gradle whitelist stays `getting-started`-only (per repo owner); Maven CI
  covers this example. The Gradle build must still pass locally.

## Architecture

Standard embedded Spring Boot 4 shape (Operaton engine in-process). The process
orchestrates two external REST services — an OpenAI-compatible **LLM** and a
**calendar free/busy API** — through the Operaton **HTTP connector**
(`http-connector`, from `operaton-engine-plugin-connect` +
`operaton-connect-http-client`).

**Hybrid connector wiring:** each external call is a single
`<operaton:connector connectorId="http-connector">` service task whose
`<operaton:inputOutput>` mappings delegate the messy parts to plain,
unit-testable Spring beans (resolved in EL as named beans):

```
inputs:  url     = ${llmProperties.chatCompletionsUrl}
         method  = POST
         headers = { Authorization: Bearer ${llmProperties.apiKey},
                     Content-Type: application/json }
         payload = ${promptBuilder.scoreRequest(candidateName, position, applicationText)}
outputs: fitScore   = ${responseParser.score(response)}
         assessment = ${responseParser.reasoning(response)}
```

No Spin/Groovy in the BPMN: the connector performs HTTP, beans build the JSON
payload and parse the JSON response. EL passes explicit process variables to
the beans (not `execution`) so the beans are trivially unit-testable.

### Provider strategy

OpenAI-compatible Chat Completions shape (`POST {base-url}/v1/chat/completions`).
`base-url`, `api-key`, `model` fully externalized. One code path works against
OpenAI, Groq (free tier), OpenRouter, or local Ollama — config only. The
calendar API likewise externalized. Both base-urls point at WireMock in the IT.

## Process model — `candidate-screening.bpmn`

Start variables: `candidateName` (String), `position` (String),
`applicationText` (String), `recruiterEmail` (String).

```
(Application received)
  → [Score application]            LLM connector → fitScore, assessment
  → <Fit score?>  3-way exclusive gateway on ${fitScore}
       ├ "score ≥ 70 (strong)"        ${fitScore >= 70}                 → set autoInvite=true ─────────┐
       ├ "score 50–69 (borderline)"   ${fitScore >= 50 && fitScore < 70}→ (Recruiter reviews application)│
       │                                 user task, candidateGroups=recruiters; form: assessment (RO),  │
       │                                 approved (Boolean)                                             │
       │                                 → <Recruiter decision?> exclusive                              │
       │                                      ├ "approve"  ${approved}  → set autoInvite=false ─────────┤→ ⊕ Gateway_InviteMerge
       │                                      └ "decline"  (default) ──────────────────────────────────┐
       └ "score < 50 (reject)"         (default) ────────────────────────────────────────────────────┐ │→ ⊕ Gateway_RejectMerge

  Gateway_InviteMerge (converging) →
     → [Query recruiter calendar]   calendar connector → interviewSlot   (SlotFinder picks first free working-day slot)
     → [Draft invitation email]     LLM connector → invitationEmail       (proposes interviewSlot)
     → <Auto-invited?> exclusive on ${autoInvite}
          ├ "automatic"  ${autoInvite} → [Draft recruiter summary email] LLM connector → recruiterSummaryEmail → (Candidate invited)
          └ "reviewed"   (default)    ───────────────────────────────────────────────────────────────────────→ (Candidate invited)

  Gateway_RejectMerge (converging) →
     → [Draft rejection email]      LLM connector → rejectionEmail → (Candidate rejected)
```

**Element inventory (ids):**

- Start: `StartEvent_ApplicationReceived`
- Service tasks (all `http-connector`): `ServiceTask_ScoreApplication`,
  `ServiceTask_QueryCalendar`, `ServiceTask_InvitationEmail`,
  `ServiceTask_RecruiterSummaryEmail`, `ServiceTask_RejectionEmail`
- User task: `UserTask_RecruiterReview` (`operaton:candidateGroups="recruiters"`,
  generated form via `operaton:formData`: `assessment` string read-only,
  `approved` boolean)
- Exclusive gateways: `Gateway_FitScore` (diverging, 3 flows, default = reject),
  `Gateway_RecruiterDecision` (diverging, default = decline),
  `Gateway_AutoInvited` (diverging, default = reviewed)
- Converging gateways: `Gateway_InviteMerge`, `Gateway_RejectMerge`
- End events: `EndEvent_Invited`, `EndEvent_Rejected`
- `autoInvite` (Boolean) is set **on the outgoing sequence flows** of
  `Gateway_FitScore` / `Gateway_RecruiterDecision` via a start
  `operaton:executionListener` of type `expression`:
  `${execution.setVariable('autoInvite', true)}` on the strong flow and the
  approve flow set `true` / `false` respectively. No extra service tasks. The
  converging-then-flag pattern keeps the invitation/email tasks single (no
  duplication), and `Gateway_AutoInvited` reads the stored flag after merge.

**Per-instance calls:** score (always) + one email path. Strong → invitation +
recruiter-summary (calendar queried). Borderline-approved → invitation only
(calendar queried, no summary). Borderline-declined / weak → rejection only
(no calendar).

## Components

| File | Responsibility |
|---|---|
| `CandidateScreeningApplication` | `@SpringBootApplication` entry point |
| `LlmProperties` | `@ConfigurationProperties("llm")` — `baseUrl`, `apiKey`, `model`; exposes `chatCompletionsUrl` (= `baseUrl` + `/v1/chat/completions`) |
| `CalendarProperties` | `@ConfigurationProperties("calendar")` — `baseUrl`, `apiKey`; exposes `freeBusyUrl` |
| `PromptBuilder` | Builds OpenAI Chat Completions JSON payloads: `scoreRequest(name, position, applicationText)`, `invitationRequest(name, position, interviewSlot)`, `rejectionRequest(name, position)`, `recruiterSummaryRequest(name, position, fitScore, assessment)`. Uses Jackson to build JSON. |
| `ResponseParser` | Jackson parse of `choices[0].message.content`: `score(response)`→Integer, `reasoning(response)`→String, `content(response)`→String. Scoring prompt instructs the model to return JSON `{"score":<int>,"reasoning":"..."}`; `score`/`reasoning` parse that inner content. |
| `CalendarRequestBuilder` | `freeBusyRequest(recruiterEmail)` — builds the calendar free/busy query JSON payload over a forward date window. Dedicated bean, referenced from the calendar connector's `payload` input mapping. |
| `SlotFinder` | `firstFreeWorkingDaySlot(response)` — parses calendar free/busy JSON, filters to Mon–Fri, returns the earliest free slot as an ISO/human string. Determinism in tests comes from fixed dates in the stubbed response. |
| `DataInitializer` | Idempotently seeds the `recruiters` group and a recruiter user (`rachel`/`rachel`), added to `recruiters`. (`recruiters` is alphanumeric — passes the engine group whitelist.) |
| `application.yaml` | Postgres datasource (5432), `operaton.bpm.admin-user` demo/demo, `llm.*` and `calendar.*` with env-var defaults (see below). |

### Configuration defaults (`application.yaml`)

```yaml
llm:
  base-url: ${LLM_BASE_URL:https://api.openai.com}
  api-key: ${LLM_API_KEY:}
  model: ${LLM_MODEL:gpt-4o-mini}
calendar:
  base-url: ${CALENDAR_BASE_URL:http://localhost:8090}
  api-key: ${CALENDAR_API_KEY:}
```

## Error handling

The invite/reject branching is the primary alternative path. LLM/calendar
transport failures are out of scope for the happy/alternative coverage; the
README notes that production hardening (async-before + boundary error events +
retries) is layered on the same model. No `operaton:class`; service logic that
is not a connector uses `delegateExpression` per §4.

## Dependencies

- `spring-boot-starter` (web not required; engine + connector only — keep
  minimal), `operaton-bpm-spring-boot-starter`, Postgres driver.
- `org.operaton.bpm:operaton-engine-plugin-connect` and
  `org.operaton.connect:operaton-connect-http-client` — register `http-connector`.
- Jackson (transitive via Spring Boot) for `PromptBuilder`/`ResponseParser`.
- Test: JUnit Jupiter, Testcontainers `postgresql`,
  `org.wiremock.integrations.testcontainers:wiremock-testcontainers-module:1.0-alpha-13`,
  AssertJ. (No Awaitility — connector calls are synchronous; instances reach
  the user task or an end event synchronously on start/complete.)
- `maven-failsafe-plugin` with `integration-test` + `verify` goals.

## Testing — `CandidateScreeningIT`

`@Testcontainers`: PostgreSQL (`@ServiceConnection`) + a WireMock container.
`@DynamicPropertySource` points `llm.base-url` and `calendar.base-url` at the
WireMock base URL. WireMock stubs:

- `POST /v1/chat/completions` — matched by distinctive prompt content
  (system/user text) to return: a scoring completion whose `content` is
  `{"score":88,...}` for the strong fixture, `{"score":60,...}` for borderline,
  `{"score":35,...}` for weak; and invitation / rejection / recruiter-summary
  completions for the email prompts.
- `POST` (calendar free/busy endpoint) — returns a fixed free/busy document
  with a known free working-day slot, so `SlotFinder` is deterministic.

Tests (all assert the expected end event + key variables; run end-to-end on
real Postgres):

1. `strongCandidateIsAutoInvited` — start with strong `applicationText` →
   `fitScore == 88` → calendar queried (`interviewSlot` set) → `invitationEmail`
   and `recruiterSummaryEmail` non-blank → ends at `EndEvent_Invited`.
2. `weakCandidateIsRejected` — weak fixture → `fitScore < 50` → `rejectionEmail`
   non-blank, `interviewSlot` not set → ends at `EndEvent_Rejected`.
3. `borderlineApprovedIsInvited` — borderline fixture → `fitScore` in [50,70) →
   `UserTask_RecruiterReview` exists with `assessment` variable populated →
   complete with `approved=true` → `invitationEmail` set, `recruiterSummaryEmail`
   **not** set → ends at `EndEvent_Invited`.
4. `borderlineDeclinedIsRejected` — borderline fixture → complete recruiter task
   with `approved=false` → ends at `EndEvent_Rejected`.

## Local run

- `docker-compose.yml`: **PostgreSQL only** (health-checked, host 5432). The LLM
  and calendar are external services the user configures.
- `docker compose up -d` then `./mvnw spring-boot:run` / `./gradlew bootRun`.
  Cockpit/Tasklist at http://localhost:8080 (demo/demo).
- README documents three ways to supply a real LLM via env vars, with links:
  **OpenAI** (paid; platform.openai.com API keys), **Groq** (free tier,
  OpenAI-compatible; console.groq.com), **Ollama** (fully local;
  `base-url=http://localhost:11434`). The calendar API is documented as an
  OpenAI-style free/busy stub the user can point at any compatible endpoint (or
  the bundled WireMock mappings reused locally).
- Walk-through: `curl` to start the process with a strong vs borderline vs weak
  `applicationText`; Tasklist to complete the recruiter review on the borderline
  case.

## Documentation & housekeeping

- README: 8 standard sections; embed `src/main/resources/candidate-screening.png`
  rendered by `scripts/render-bpmn.sh`; register that PNG in
  `.operaton-starter.yml` `screenshots`.
- Root `README.md` use-cases table gets a row:
  `[candidate-screening](examples/use-cases/candidate-screening) | AI recruiting screening | LLM scoring + email generation via HTTP connector, LLM-driven confidence gateway, human-in-the-loop on borderline, calendar slot query, WireMock IT`.

## Scope justification

Larger than a minimal example, but every element traces to an explicit
requirement and the whole teaches one coherent real-world genAI-governance
pattern: **auto-act on high confidence, escalate the uncertain band to a human,
keep humans informed of automated actions, and orchestrate multiple external
services (LLM + calendar) through the HTTP connector.**
