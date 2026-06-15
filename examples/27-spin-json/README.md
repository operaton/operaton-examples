# 27 — Spin JSON Variables

Demonstrates JSON-typed process variables using Operaton Spin: store Java objects
as JSON in the process engine and read them back as typed instances in delegates.

## What you will learn

- Use `Variables.objectValue(...).serializationDataFormat("application/json")` to store a Java object as a JSON process variable
- Read a JSON-serialized variable back as a typed Java object in a `JavaDelegate` via `execution.getVariable()`
- Understand that the Spin JSON data format plugin is included automatically with the Spring Boot starter — no extra dependency is needed
- Verify JSON variable round-trip behavior end-to-end with Testcontainers (real PostgreSQL)

## Process model

![Process diagram](src/main/resources/loan-application.png)

## Prerequisites

- JDK 21
- Docker (for PostgreSQL — both for local runs and the integration tests)

## Run it

```bash
docker compose up -d --wait
./mvnw spring-boot:run      # or: ./gradlew bootRun
```

Open http://localhost:8080 — Cockpit and Tasklist, login `demo` / `demo`.

## Walk through it

1. Start a loan application by posting a JSON variable via the REST API:
   ```bash
   curl -u demo:demo -H 'Content-Type: application/json' \
     -d '{
       "variables": {
         "application": {
           "value": "{\"applicantName\":\"Alice Smith\",\"amount\":10000.0,\"termMonths\":36,\"purpose\":\"Home renovation\"}",
           "type": "String",
           "valueInfo": {
             "serializationDataFormat": "application/json",
             "objectTypeName": "org.operaton.examples.spinjson.LoanApplication"
           }
         }
       }
     }' \
     http://localhost:8080/engine-rest/process-definition/key/loan-application/start
   ```
2. In Cockpit, open the completed instance — the **Variables** tab shows `application`
   stored as type `Object` with serialization format `application/json`, alongside the
   calculated `applicationValid`, `annualInterestRate`, and `monthlyPayment` variables.
3. Inspect the JSON value stored in the database: Cockpit's variable detail view shows
   the serialized JSON string, demonstrating that the object was stored as JSON and
   deserialized transparently in each delegate.

## How it works

- [loan-application.bpmn](src/main/resources/loan-application.bpmn) defines two
  sequential service tasks: **Validate application** and **Prepare offer**.
- [LoanApplication](src/main/java/org/operaton/examples/spinjson/LoanApplication.java)
  is a plain Java class (POJO with no-arg constructor and getters/setters) that Jackson
  can serialize and deserialize automatically.
- [ValidateApplicationDelegate](src/main/java/org/operaton/examples/spinjson/ValidateApplicationDelegate.java)
  reads the `application` variable with `execution.getVariable("application")` — Spin
  deserializes the stored JSON back to a `LoanApplication` instance transparently —
  then sets `applicationValid`.
- [PrepareOfferDelegate](src/main/java/org/operaton/examples/spinjson/PrepareOfferDelegate.java)
  reads the same variable, calculates `annualInterestRate` and `monthlyPayment`, and
  stores them as plain double variables.
- The Spin JSON plugin (`operaton-spin-dataformat-json-jackson`) is included
  transitively via `operaton-bpm-spring-boot-starter-webapp`; no explicit dependency
  is required.

## Run the tests

```bash
./mvnw verify        # or: ./gradlew build
```

[LoanApplicationIT](src/test/java/org/operaton/examples/spinjson/LoanApplicationIT.java)
boots the application against a Testcontainers PostgreSQL and verifies that a
`LoanApplication` object stored as a JSON variable is correctly deserialized in both
delegates, that validation produces `applicationValid=true`, and that the monthly
payment is calculated as a positive value.
