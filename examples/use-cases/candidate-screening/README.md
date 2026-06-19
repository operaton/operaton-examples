# Candidate Screening — LLM in a process

Uses an LLM (via the Operaton **HTTP connector**) inside a BPMN process to score a job application, drive a confidence-banded gateway, generate candidate and recruiter emails, and propose an interview slot from the recruiter's calendar — escalating the uncertain score band to a human.

## What you will learn

- How to call an OpenAI-compatible LLM over REST from a BPMN service task using the `http-connector`
- How to keep JSON building/parsing in clean Spring beans referenced from connector input/output mappings (no Spin/Groovy in the model)
- How to let an LLM **drive a gateway decision** (a fit score) and escalate the borderline band to a human task
- How to orchestrate a second external service (a calendar free/busy API) in the same process
- How to use **BPMN send tasks** to dispatch SMTP email from a process via Spring Mail
- How to test LLM/calendar/email integrations deterministically with WireMock + Testcontainers + Mailpit

## Process model

![Process diagram](src/main/resources/candidate-screening.png)

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 |
| Docker | any recent version (required for tests and local run) |

## Run it

Start the local stack (PostgreSQL + a local Ollama LLM + a calendar stub):

```bash
cd examples/use-cases/candidate-screening
docker compose up -d
```

The first start downloads the `llama3.2` model (~2 GB) via the `ollama-pull` helper; wait for it to finish (`docker compose logs -f ollama-pull`). On low-resource machines use a smaller model: `LLM_MODEL=llama3.2:1b` (also `docker compose exec ollama ollama pull llama3.2:1b`).

Run the application:

```bash
./mvnw spring-boot:run
# or
./gradlew bootRun
```

Operaton Cockpit/Tasklist: http://localhost:8080 (demo / demo). Postgres is on 5432, Ollama on 11434, the calendar stub on 8090, **Mailpit** (email inbox) on http://localhost:8025.

### Use a hosted LLM instead of Ollama

Set environment variables before `spring-boot:run` (then you can stop the `ollama` containers):

- **OpenAI** (paid — get a key at https://platform.openai.com/api-keys):
  ```bash
  export LLM_BASE_URL=https://api.openai.com
  export LLM_API_KEY=sk-...
  export LLM_MODEL=gpt-4o-mini
  ```
- **Groq** (free tier, OpenAI-compatible — https://console.groq.com/keys):
  ```bash
  export LLM_BASE_URL=https://api.groq.com/openai
  export LLM_API_KEY=gsk_...
  export LLM_MODEL=llama-3.1-8b-instant
  ```

## Walk through it

Start a strong candidate (auto-invited):

```bash
curl -s -X POST http://localhost:8080/engine-rest/process-definition/key/candidate-screening/start \
  -H "Content-Type: application/json" \
  -d '{"variables": {
        "candidateName":   {"value": "Ada Lindqvist",                                "type": "String"},
        "position":        {"value": "Senior Java Engineer",                         "type": "String"},
        "applicationText": {"value": "Ten years of Java and Spring Boot leadership.", "type": "String"},
        "recruiterEmail":  {"value": "rachel@example.com",                           "type": "String"},
        "candidateEmail":  {"value": "ada.lindqvist@example.com",                    "type": "String"}
      }}'
```

A high fit score routes straight to the invitation email and a recruiter summary; the process ends at `EndEvent_Invited`. Open **Mailpit at http://localhost:8025** to see both the candidate invitation and the recruiter summary email appear in the inbox.

Start a borderline candidate (score 50–69 — goes to recruiter review):

```bash
curl -s -X POST http://localhost:8080/engine-rest/process-definition/key/candidate-screening/start \
  -H "Content-Type: application/json" \
  -d '{"variables": {
        "candidateName":   {"value": "Bea Romano",                                           "type": "String"},
        "position":        {"value": "Backend Engineer",                                     "type": "String"},
        "applicationText": {"value": "I have 3 years of Java experience building REST APIs.", "type": "String"},
        "recruiterEmail":  {"value": "rachel@example.com",                                   "type": "String"},
        "candidateEmail":  {"value": "bea.romano@example.com",                               "type": "String"}
      }}'
```

Log in to Tasklist as `rachel` / `rachel`, open **Recruiter reviews application**, read the LLM assessment, set **Approve invitation?** and complete. The invitation email is delivered and appears in Mailpit.

A weak candidate (e.g. `"applicationText": "Recent graduate, no Java experience."`) ends at `EndEvent_Rejected` immediately; only the rejection email is delivered.

## How it works

- **`ServiceTask_ScoreApplication`** posts an OpenAI Chat Completions request via `http-connector`; `${promptBuilder.scoreRequest(...)}` builds the JSON (with `response_format: json_object`), `${responseParser.score(response)}` / `${responseParser.reasoning(response)}` set `fitScore` and `assessment`. See [PromptBuilder.java](src/main/java/org/operaton/examples/candidatescreening/PromptBuilder.java) and [ResponseParser.java](src/main/java/org/operaton/examples/candidatescreening/ResponseParser.java).
- **`Gateway_FitScore`** branches on `${fitScore}`: ≥ 70 auto-invite, 50–69 to **`UserTask_RecruiterReview`**, < 50 reject. Sequence-flow execution listeners set `autoInvite`.
- **`ServiceTask_QueryCalendar`** posts a free/busy query; [SlotFinder.java](src/main/java/org/operaton/examples/candidatescreening/SlotFinder.java) returns the earliest free working-day slot as `interviewSlot`.
- **`ServiceTask_InvitationEmail`** / **`ServiceTask_RejectionEmail`** / **`ServiceTask_RecruiterSummaryEmail`** generate email text via the same connector pattern. Each is followed by a BPMN **send task** (`SendTask_SendInvitation` etc.) that calls `emailDispatcher.send*(...)` — a Spring `@Component` backed by Spring Boot's auto-configured `JavaMailSender`. The recruiter summary is drafted and sent only on the automatic (≥ 70) path (`Gateway_AutoInvited`). See [EmailDispatcher.java](src/main/java/org/operaton/examples/candidatescreening/EmailDispatcher.java).
- The connect plugin is enabled in [application.yaml](src/main/resources/application.yaml) via `operaton.bpm.process-engine-plugins`.

## Run the tests

```bash
./mvnw verify
# or
./gradlew build
```

`CandidateScreeningDeploymentIT` proves the process deploys with the connect plugin and seeds the `recruiters` group. `CandidateScreeningIT` runs the process end-to-end against PostgreSQL + WireMock (LLM + calendar stubs) + a real **Mailpit** container for all four paths: strong→invited (2 emails), weak→rejected (1 email), borderline-approved→invited (1 email), borderline-declined→rejected (1 email).
