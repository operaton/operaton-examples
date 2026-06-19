# Candidate Screening — Email Sending Extension — Design

**Goal:** Extend the existing `uc-05-candidate-screening` example to actually
deliver the three LLM-drafted emails (invitation, rejection, recruiter summary)
via SMTP, using BPMN **send tasks** and **Mailpit** as the local catch-all mail
server.

**Demonstrated concept (README addition):** Using BPMN send tasks to dispatch
Spring Mail messages from within a process — LLM drafts the text, the process
engine routes and sends it, Mailpit catches everything locally so no real email
is delivered during development or testing.

## Scope

This is an additive extension to
`examples/use-cases/candidate-screening`. Nothing in the existing module is
removed; the only structural changes are:

- A new required start variable (`candidateEmail`).
- Three new BPMN send tasks inserted after each existing email-draft service task.
- One new Spring `@Component` (`EmailDispatcher`).
- Spring Mail auto-configuration wired into `application.yaml`.
- Mailpit added to `docker-compose.yml`.
- IT updated: Mailpit Testcontainer + email delivery assertions + `candidateEmail` in all fixtures.
- README updated: Mailpit walk-through, updated `curl` examples, borderline-candidate example.

## Global constraints (inherited from parent spec)

Same as `2026-06-19-candidate-screening-llm-design.md`:

- Spring Boot **4.1.0**, Operaton **2.1.1**, Java **21**.
- Dependency management via BOMs only. Never pin a version a BOM manages.
- Dual build: `./mvnw verify` (failsafe `*IT`, count > 0) **and** `./gradlew build` both green.
- BPMN: `xmlns:operaton="http://operaton.org/schema/1.0/bpmn"`; `isExecutable="true"`;
  `operaton:historyTimeToLive="P30D"`; full BPMN DI; all elements named;
  `bpmn:extensionElements` appears **before** `bpmn:conditionExpression` on
  every sequence flow.
- No `operaton:class`; service logic that is not a connector uses
  `delegateExpression` (or `expression` for void method calls).
- `application.yaml`; no H2; no `Thread.sleep`.
- CI Gradle whitelist stays `getting-started`-only — Maven CI covers this module.

## Process model changes

### New start variable

`candidateEmail` (String) — required alongside the existing four
(`candidateName`, `position`, `applicationText`, `recruiterEmail`).

### New BPMN elements

Three `<bpmn:sendTask>` elements are inserted immediately after each
draft service task:

| New element ID | Inserted after | `operaton:expression` |
|---|---|---|
| `SendTask_SendInvitation` | `ServiceTask_InvitationEmail` | `${emailDispatcher.sendInvitation(candidateEmail, candidateName, position, invitationEmail, interviewSlot)}` |
| `SendTask_SendRecruiterSummary` | `ServiceTask_RecruiterSummaryEmail` | `${emailDispatcher.sendRecruiterSummary(recruiterEmail, candidateName, position, recruiterSummaryEmail)}` |
| `SendTask_SendRejection` | `ServiceTask_RejectionEmail` | `${emailDispatcher.sendRejection(candidateEmail, candidateName, position, rejectionEmail)}` |

All three send tasks must have `name` attributes:
- `Send invitation email`
- `Send recruiter summary email`
- `Send rejection email`

The existing service task IDs, gateway IDs, and sequence flow IDs are unchanged.

Updated flow snippets:

```
[ServiceTask_InvitationEmail] → [SendTask_SendInvitation] → [Gateway_AutoInvited]
[ServiceTask_RecruiterSummaryEmail] → [SendTask_SendRecruiterSummary] → [EndEvent_Invited]
[ServiceTask_RejectionEmail] → [SendTask_SendRejection] → [EndEvent_Rejected]
```

New sequence flow IDs:
- `Flow_InvitationToSend` (ServiceTask_InvitationEmail → SendTask_SendInvitation)
- `Flow_SendToAutoInvited` (SendTask_SendInvitation → Gateway_AutoInvited)
- `Flow_RecruiterToSend` (ServiceTask_RecruiterSummaryEmail → SendTask_SendRecruiterSummary)
- `Flow_SendRecruiterToEnd` (SendTask_SendRecruiterSummary → EndEvent_Invited)
- `Flow_RejectionToSend` (ServiceTask_RejectionEmail → SendTask_SendRejection)
- `Flow_SendRejectionToEnd` (SendTask_SendRejection → EndEvent_Rejected)

Old direct sequence flows that are replaced:
- `Flow_InvitationToAutoInvited` (ServiceTask_InvitationEmail → Gateway_AutoInvited) — removed
- `Flow_RecruiterSummaryToEnd` (ServiceTask_RecruiterSummaryEmail → EndEvent_Invited) — removed
- `Flow_RejectionToEnd` (ServiceTask_RejectionEmail → EndEvent_Rejected) — removed

(The exact IDs of the old flows must be read from the current BPMN file before editing.)

## New components

### `EmailDispatcher`

```java
package org.operaton.examples.candidatescreening;

@Component
public class EmailDispatcher {
    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;

    public EmailDispatcher(JavaMailSender javaMailSender, MailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.mailProperties = mailProperties;
    }

    public void sendInvitation(String to, String candidateName, String position,
                               String body, String interviewSlot) {
        send(to, "Interview Invitation: " + position, body);
    }

    public void sendRecruiterSummary(String to, String candidateName, String position,
                                     String body) {
        send(to, "Candidate Summary: " + candidateName + " — " + position, body);
    }

    public void sendRejection(String to, String candidateName, String position,
                              String body) {
        send(to, "Application Update: " + position, body);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailProperties.getFrom());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        javaMailSender.send(msg);
    }
}
```

### `MailProperties`

```java
package org.operaton.examples.candidatescreening;

@Component
@ConfigurationProperties("mail")
public class MailProperties {
    private String from = "screening@example.com";
    // getter + setter
}
```

## Configuration

`application.yaml` additions (under existing top-level keys):

```yaml
spring:
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}

mail:
  from: ${MAIL_FROM:screening@example.com}
```

## Dependencies

New entries in `pom.xml` (no version — managed by Spring Boot BOM):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

No new entry needed in `build.gradle.kts` beyond the equivalent:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-mail")
```

## docker-compose.yml

Add Mailpit service:

```yaml
mailpit:
  image: axllent/mailpit:v1.24
  ports:
    - "1025:1025"   # SMTP — Spring Mail connects here
    - "8025:8025"   # HTTP UI — browse received mail at http://localhost:8025
  healthcheck:
    test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8025/livez"]
    interval: 5s
    timeout: 3s
    retries: 10
```

The Spring Boot app needs `MAIL_HOST=mailpit` when run inside docker-compose.
Document this in the README local-run section as an env-var override (or add a
`candidate-screening` service block to the compose file that sets `MAIL_HOST`).

## Testing — `CandidateScreeningIT` changes

### New container

```java
@Container
static GenericContainer<?> mailpit =
    new GenericContainer<>("axllent/mailpit:v1.24")
        .withExposedPorts(1025, 8025);
```

### `WireMockInitializer` extension

Add alongside the existing `llm.base-url` and `calendar.base-url` injections:

```java
TestPropertyValues.of(
    "llm.base-url=...",
    "calendar.base-url=...",
    "spring.mail.host=" + mailpit.getHost(),
    "spring.mail.port=" + mailpit.getMappedPort(1025)
).applyTo(ctx.getEnvironment());
```

### Helper: Mailpit messages API

```java
private int mailpitMessageCount() throws Exception {
    String url = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages";
    // Use RestTemplate or HttpClient; parse JSON `total` field
}

private void deleteAllMailpitMessages() throws Exception {
    // DELETE http://<mailpit>/api/v1/messages  (Mailpit v1 API)
}
```

Call `deleteAllMailpitMessages()` in a `@BeforeEach` to isolate test mail counts.

### Updated test fixtures

All four tests add `candidateEmail` to the start variables map:
```java
variables.put("candidateEmail", Map.of("value", "candidate@example.com", "type", "String"));
```

### Email delivery assertions (per test)

| Test | Expected deliveries | Recipient |
|---|---|---|
| `strongCandidateIsAutoInvited` | invitation + recruiter summary | invitation → `candidate@example.com`; summary → `rachel@example.com` |
| `weakCandidateIsRejected` | rejection × 1 | `candidate@example.com` |
| `borderlineApprovedIsInvited` | invitation × 1 | `candidate@example.com` |
| `borderlineDeclinedIsRejected` | rejection × 1 | `candidate@example.com` |

Assertion style (non-fragile):
```java
assertThat(mailpitMessageCount()).isEqualTo(2); // strong: invitation + summary
```

Optionally assert the `To` address of each message for precision.

## README walk-through changes

### Updated `curl` example (strong candidate)

Add `candidateEmail` field to the existing examples.

### New borderline `curl` example

```bash
# Borderline candidate — goes to recruiter review
curl -X POST http://localhost:8080/engine-rest/process-definition/key/candidate-screening/start \
  -H "Content-Type: application/json" \
  -d '{
    "variables": {
      "candidateName":   {"value": "Bea Romano",                                        "type": "String"},
      "position":        {"value": "Backend Engineer",                                  "type": "String"},
      "applicationText": {"value": "I have 3 years of Java experience building REST APIs.", "type": "String"},
      "recruiterEmail":  {"value": "rachel@example.com",                                "type": "String"},
      "candidateEmail":  {"value": "bea.romano@example.com",                            "type": "String"}
    }
  }'
```

### Mailpit walk-through step

Add after "start the stack":

> Open Mailpit at http://localhost:8025 to view all outgoing emails.
> After starting a candidate process, watch the inbox populate with
> the invitation or rejection email delivered by the process engine.

## BPMN DI

The three new send tasks need BPMN DI shape elements (bounds) and new
`<bpmn:waypoint>` entries on the connecting sequence flows. Layout:
place each send task horizontally between its predecessor service task
and its successor gateway/end event, maintaining the existing vertical
track.

## Scope boundaries

- No real SMTP relay — Mailpit catches everything locally.
- No email templates (Thymeleaf etc.) — the LLM-drafted text is the body.
- No retry/error boundary on send failures — out of scope per parent spec.
- BPMN PNG re-render required after BPMN changes (existing `scripts/render-bpmn.sh`).
