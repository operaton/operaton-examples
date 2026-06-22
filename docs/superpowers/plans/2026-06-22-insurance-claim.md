# Insurance Claim (uc-06) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `examples/use-cases/insurance-claim` — a Spring Boot + Operaton use-case example showcasing the event-based gateway (message vs timer race) and parallel gateway (concurrent fraud check + damage appraisal) as the two primary uncovered BPMN concepts in the catalog.

**Architecture:** Spring Boot 4.1.0 embedded Operaton webapp; PostgreSQL via Testcontainers in ITs; four delegate beans handle service tasks; DMN FIRST hit policy evaluates claim settlement. No external services required — all logic is in-process.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Operaton 2.1.1, Testcontainers 2.0.5, AssertJ, Awaitility (no version — Spring Boot BOM).

## Global Constraints

- Java 21, Spring Boot 4.1.0, Operaton 2.1.1 — versions must match identically in `pom.xml` and `build.gradle.kts`.
- Maven artifact: `groupId=org.operaton.examples`, `artifactId=uc-06-insurance-claim`, `version=0.1.0-SNAPSHOT`.
- Java package: `org.operaton.examples.insuranceclaim` (no hyphen, no number prefix).
- Module directory: `examples/use-cases/insurance-claim` (relative to `operaton-examples/` repo root).
- Parent: `operaton-examples-aggregate` with `<relativePath>../../../pom.xml</relativePath>`.
- BPMN process id: `insurance-claim`; DMN decision id: `claim-settlement`.
- Message name (BPMN + correlation): `documentsReceived`.
- BPMN/DMN: use `xmlns:operaton="http://operaton.org/schema/1.0/bpmn"` — never `camunda` namespace.
- Every process element has a meaningful `name`; process has `operaton:historyTimeToLive="P30D"`.
- Service tasks use `operaton:delegateExpression="${beanName}"` — never `operaton:class`.
- No `Thread.sleep` in tests — use Awaitility.
- ITs use Testcontainers PostgreSQL only (no WireMock — no external services needed).
- `./mvnw verify` and `./gradlew build` must both pass from a clean checkout.
- Admin user: `demo/demo` via `operaton.bpm.admin-user`. No additional users or groups.
- `application.yaml` (not `.properties`).

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `examples/use-cases/insurance-claim/pom.xml` | Create | Maven build descriptor |
| `examples/use-cases/insurance-claim/build.gradle.kts` | Create | Gradle build descriptor |
| `examples/use-cases/insurance-claim/settings.gradle.kts` | Create | Gradle root project name |
| `examples/use-cases/insurance-claim/.mvn/wrapper/maven-wrapper.properties` | Create | Maven wrapper config |
| `examples/use-cases/insurance-claim/.mvn/wrapper/maven-wrapper.jar` | Copy | Maven wrapper binary |
| `examples/use-cases/insurance-claim/mvnw` | Copy | Maven wrapper script |
| `examples/use-cases/insurance-claim/mvnw.cmd` | Copy | Maven wrapper Windows |
| `examples/use-cases/insurance-claim/gradle/wrapper/gradle-wrapper.properties` | Create | Gradle wrapper config |
| `examples/use-cases/insurance-claim/gradle/wrapper/gradle-wrapper.jar` | Copy | Gradle wrapper binary |
| `examples/use-cases/insurance-claim/gradlew` | Copy | Gradle wrapper script |
| `examples/use-cases/insurance-claim/gradlew.bat` | Copy | Gradle wrapper Windows |
| `examples/use-cases/insurance-claim/docker-compose.yml` | Create | Local Postgres service |
| `examples/use-cases/insurance-claim/src/main/resources/application.yaml` | Create | App config |
| `examples/use-cases/insurance-claim/src/main/resources/insurance-claim.bpmn` | Create (stub → full) | Process model |
| `examples/use-cases/insurance-claim/src/main/resources/claim-settlement.dmn` | Create (Task 2) | Settlement DMN |
| `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/InsuranceClaimApplication.java` | Create | Spring Boot main class |
| `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/RequestDocumentsDelegate.java` | Create (Task 3) | Service task bean |
| `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/FraudCheckDelegate.java` | Create (Task 3) | Service task bean |
| `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/AppraiseDamageDelegate.java` | Create (Task 3) | Service task bean |
| `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/CloseIncompleteClaimDelegate.java` | Create (Task 3) | Service task bean |
| `examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim/InsuranceClaimIT.java` | Create + extend | Integration tests |
| `examples/use-cases/insurance-claim/README.md` | Create (Task 5) | Example documentation |
| `pom.xml` (repo root) | Modify | Add module entry |
| `settings.gradle.kts` (repo root) | Modify | Add include + projectDir |
| `.operaton-starter.yml` (repo root) | Modify (Task 5) | Registry entry |
| `README.md` (repo root) | Modify (Task 5) | BPMN concept mapping table |

---

## Task 1: Module scaffold + aggregator registration

**Files:**
- Create: `examples/use-cases/insurance-claim/pom.xml`
- Create: `examples/use-cases/insurance-claim/build.gradle.kts`
- Create: `examples/use-cases/insurance-claim/settings.gradle.kts`
- Create: `examples/use-cases/insurance-claim/docker-compose.yml`
- Create: `examples/use-cases/insurance-claim/src/main/resources/application.yaml`
- Create: `examples/use-cases/insurance-claim/src/main/resources/insurance-claim.bpmn` (minimal stub)
- Create: `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/InsuranceClaimApplication.java`
- Create: `examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim/InsuranceClaimIT.java`
- Copy build wrappers from `examples/use-cases/order-fulfillment/`
- Modify: `pom.xml` (repo root) — add module
- Modify: `settings.gradle.kts` (repo root) — add include + projectDir

**Interfaces:**
- Produces: deployed `insurance-claim` process; `InsuranceClaimIT` class with `processDefinitionIsDeployed()` test passing.

- [ ] **Step 1: Create the module directory tree**

```bash
mkdir -p examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim
mkdir -p examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim
mkdir -p examples/use-cases/insurance-claim/src/main/resources
mkdir -p examples/use-cases/insurance-claim/.mvn/wrapper
mkdir -p examples/use-cases/insurance-claim/gradle/wrapper
```

Run from: `operaton-examples/` repo root.

- [ ] **Step 2: Copy build wrappers from order-fulfillment**

```bash
cp examples/use-cases/order-fulfillment/mvnw examples/use-cases/insurance-claim/mvnw
cp examples/use-cases/order-fulfillment/mvnw.cmd examples/use-cases/insurance-claim/mvnw.cmd
cp examples/use-cases/order-fulfillment/.mvn/wrapper/maven-wrapper.jar examples/use-cases/insurance-claim/.mvn/wrapper/maven-wrapper.jar
cp examples/use-cases/order-fulfillment/.mvn/wrapper/maven-wrapper.properties examples/use-cases/insurance-claim/.mvn/wrapper/maven-wrapper.properties
cp examples/use-cases/order-fulfillment/gradlew examples/use-cases/insurance-claim/gradlew
cp examples/use-cases/order-fulfillment/gradlew.bat examples/use-cases/insurance-claim/gradlew.bat
cp examples/use-cases/order-fulfillment/gradle/wrapper/gradle-wrapper.jar examples/use-cases/insurance-claim/gradle/wrapper/gradle-wrapper.jar
cp examples/use-cases/order-fulfillment/gradle/wrapper/gradle-wrapper.properties examples/use-cases/insurance-claim/gradle/wrapper/gradle-wrapper.properties
chmod +x examples/use-cases/insurance-claim/mvnw examples/use-cases/insurance-claim/gradlew
```

- [ ] **Step 3: Create `pom.xml`**

Create `examples/use-cases/insurance-claim/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.operaton.examples</groupId>
    <artifactId>operaton-examples-aggregate</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../../pom.xml</relativePath>
  </parent>

  <groupId>org.operaton.examples</groupId>
  <artifactId>uc-06-insurance-claim</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>Operaton Example: Insurance Claim</name>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <spring-boot.version>4.1.0</spring-boot.version>
    <operaton.version>2.1.1</operaton.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.operaton.bpm</groupId>
        <artifactId>operaton-bom</artifactId>
        <version>${operaton.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>2.0.5</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.operaton.bpm.springboot</groupId>
      <artifactId>operaton-bpm-spring-boot-starter-webapp</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-testcontainers</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <version>${spring-boot.version}</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
      </plugin>
    </plugins>
  </build>

</project>
```

- [ ] **Step 4: Create `build.gradle.kts`**

Create `examples/use-cases/insurance-claim/build.gradle.kts`:

```kotlin
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
}

group = "org.operaton.examples"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val operatonVersion = "2.1.1"

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.operaton.bpm:operaton-bom:$operatonVersion"))
    implementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))

    implementation("org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter-webapp")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.awaitility:awaitility")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 5: Create `settings.gradle.kts`**

Create `examples/use-cases/insurance-claim/settings.gradle.kts`:

```kotlin
rootProject.name = "uc-06-insurance-claim"
```

- [ ] **Step 6: Create `docker-compose.yml`**

Create `examples/use-cases/insurance-claim/docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16-alpine
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

- [ ] **Step 7: Create `application.yaml`**

Create `examples/use-cases/insurance-claim/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: insurance-claim
  datasource:
    url: jdbc:postgresql://localhost:5432/operaton
    username: operaton
    password: operaton

operaton:
  bpm:
    admin-user:
      id: demo
      password: demo
      first-name: Demo
    filter:
      create: All tasks
```

- [ ] **Step 8: Create `InsuranceClaimApplication.java`**

Create `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/InsuranceClaimApplication.java`:

```java
package org.operaton.examples.insuranceclaim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InsuranceClaimApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsuranceClaimApplication.class, args);
    }
}
```

- [ ] **Step 9: Create the minimal BPMN stub**

Create `examples/use-cases/insurance-claim/src/main/resources/insurance-claim.bpmn`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:operaton="http://operaton.org/schema/1.0/bpmn"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  id="Definitions_InsuranceClaim"
                  targetNamespace="http://operaton.org/examples/insurance-claim">

  <bpmn:process id="insurance-claim"
                name="Insurance Claim"
                isExecutable="true"
                operaton:historyTimeToLive="P30D">

    <bpmn:startEvent id="StartEvent_ClaimSubmitted" name="Claim submitted">
      <bpmn:outgoing>Flow_Start_To_End</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:endEvent id="EndEvent_ClaimSettled" name="Claim settled">
      <bpmn:incoming>Flow_Start_To_End</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="Flow_Start_To_End"
                       sourceRef="StartEvent_ClaimSubmitted"
                       targetRef="EndEvent_ClaimSettled"/>
  </bpmn:process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="insurance-claim">
      <bpmndi:BPMNShape id="Shape_StartEvent" bpmnElement="StartEvent_ClaimSubmitted">
        <dc:Bounds x="152" y="82" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="135" y="125" width="72" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_EndSettled" bpmnElement="EndEvent_ClaimSettled">
        <dc:Bounds x="252" y="82" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="237" y="125" width="66" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Edge_Start_End" bpmnElement="Flow_Start_To_End">
        <di:waypoint x="188" y="100"/>
        <di:waypoint x="252" y="100"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

- [ ] **Step 10: Write the smoke IT**

Create `examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim/InsuranceClaimIT.java`:

```java
package org.operaton.examples.insuranceclaim;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class InsuranceClaimIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;
    @Autowired RuntimeService runtimeService;
    @Autowired HistoryService historyService;

    @Test
    void processDefinitionIsDeployed() {
        assertThat(processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("insurance-claim")
            .count()).isEqualTo(1);
    }
}
```

- [ ] **Step 11: Run test to verify it fails (then succeeds after startup)**

```bash
cd examples/use-cases/insurance-claim && ./mvnw verify
```

Expected: BUILD SUCCESS; `Tests run: 1, Failures: 0, Errors: 0` in failsafe output.

If the test fails with "No suitable driver found" or datasource issues, the Testcontainers `@ServiceConnection` is not overriding the datasource — ensure `spring-boot-testcontainers` is on the test classpath.

- [ ] **Step 12: Register module in root aggregator**

In `pom.xml` (repo root), add after the `examples/use-cases/order-fulfillment` module:

```xml
    <module>examples/use-cases/insurance-claim</module>
```

In `settings.gradle.kts` (repo root), add to the `include(...)` block:

```
  "examples:use-cases:insurance-claim",
```

And add a `projectDir` line after the order-fulfillment entry:

```kotlin
project(":examples:use-cases:insurance-claim").projectDir = file("examples/use-cases/insurance-claim")
```

- [ ] **Step 13: Verify the root build still compiles**

```bash
./mvnw compile -pl examples/use-cases/insurance-claim --also-make
```

Expected: BUILD SUCCESS.

- [ ] **Step 14: Commit**

```bash
git add examples/use-cases/insurance-claim/ pom.xml settings.gradle.kts
git commit -m "feat(insurance-claim): module scaffold, dual build, smoke IT"
```

---

## Task 2: DMN decision + evaluation tests

**Files:**
- Create: `examples/use-cases/insurance-claim/src/main/resources/claim-settlement.dmn`
- Modify: `examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim/InsuranceClaimIT.java`

**Interfaces:**
- Consumes: deployed Operaton engine (from Task 1).
- Produces: `claim-settlement` DMN decision; test method `settlementDecisionMapsCorrectly()` passing.

- [ ] **Step 1: Write the failing DMN test first**

Add to `InsuranceClaimIT.java`, after the `processDefinitionIsDeployed` method:

```java
    @Test
    void settlementDecisionMapsCorrectly() {
        var ds = processEngine.getDecisionService();

        // fraudSuspected → always reject regardless of other inputs
        var result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", true)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 500.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(0.0);

        // flood → reject regardless of amount or fraud
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "flood")
                .putValue("appraisedAmount", 5000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(0.0);

        // small collision (<=1000) → approve, full appraised amount
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 720.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("approve");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(720.0);

        // medium collision (<=50000) → approve at 80%
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 5000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("approve");
        assertThat((Double) result.getEntry("approvedAmount")).isEqualTo(4000.0);

        // large amount (>50000) → reject
        result = ds.evaluateDecisionByKey("claim-settlement")
            .variables(org.operaton.bpm.engine.variable.Variables.createVariables()
                .putValue("fraudSuspected", false)
                .putValue("claimType", "collision")
                .putValue("appraisedAmount", 60000.0))
            .evaluate().getSingleResult();
        assertThat((String) result.getEntry("settlementDecision")).isEqualTo("reject");
    }
```

- [ ] **Step 2: Run test — verify it fails**

```bash
cd examples/use-cases/insurance-claim && ./mvnw verify
```

Expected: FAIL — `DecisionEvaluationException: decision with key 'claim-settlement' not found`.

- [ ] **Step 3: Create `claim-settlement.dmn`**

Create `examples/use-cases/insurance-claim/src/main/resources/claim-settlement.dmn`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
             xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
             xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/"
             xmlns:operaton="http://operaton.org/schema/1.0/dmn"
             id="claim-settlement-definitions"
             name="Claim Settlement"
             namespace="http://operaton.org/examples/insurance-claim">

  <decision id="claim-settlement" name="Claim Settlement" operaton:historyTimeToLive="30">
    <decisionTable id="decisionTable_settlement" hitPolicy="FIRST">

      <input id="input_fraud" label="Fraud suspected">
        <inputExpression id="ie_fraud" typeRef="boolean">
          <text>fraudSuspected</text>
        </inputExpression>
      </input>
      <input id="input_type" label="Claim type">
        <inputExpression id="ie_type" typeRef="string">
          <text>claimType</text>
        </inputExpression>
      </input>
      <input id="input_amount" label="Appraised amount">
        <inputExpression id="ie_amount" typeRef="double">
          <text>appraisedAmount</text>
        </inputExpression>
      </input>

      <output id="output_decision" label="Settlement decision"
              name="settlementDecision" typeRef="string"/>
      <output id="output_amount" label="Approved amount"
              name="approvedAmount" typeRef="double"/>

      <!-- Rule 1: fraud suspected → reject regardless -->
      <rule id="rule_fraud">
        <inputEntry id="ie_fraud_1"><text>true</text></inputEntry>
        <inputEntry id="ie_type_1"><text></text></inputEntry>
        <inputEntry id="ie_amount_1"><text></text></inputEntry>
        <outputEntry id="oe_decision_1"><text>"reject"</text></outputEntry>
        <outputEntry id="oe_amount_1"><text>0</text></outputEntry>
      </rule>

      <!-- Rule 2: flood → reject regardless -->
      <rule id="rule_flood">
        <inputEntry id="ie_fraud_2"><text></text></inputEntry>
        <inputEntry id="ie_type_2"><text>"flood"</text></inputEntry>
        <inputEntry id="ie_amount_2"><text></text></inputEntry>
        <outputEntry id="oe_decision_2"><text>"reject"</text></outputEntry>
        <outputEntry id="oe_amount_2"><text>0</text></outputEntry>
      </rule>

      <!-- Rule 3: small amount (<=1000) → approve full appraised -->
      <rule id="rule_small">
        <inputEntry id="ie_fraud_3"><text></text></inputEntry>
        <inputEntry id="ie_type_3"><text></text></inputEntry>
        <inputEntry id="ie_amount_3"><text>&lt;= 1000</text></inputEntry>
        <outputEntry id="oe_decision_3"><text>"approve"</text></outputEntry>
        <outputEntry id="oe_amount_3"><text>appraisedAmount</text></outputEntry>
      </rule>

      <!-- Rule 4: medium amount (<=50000) → approve at 80% -->
      <rule id="rule_medium">
        <inputEntry id="ie_fraud_4"><text></text></inputEntry>
        <inputEntry id="ie_type_4"><text></text></inputEntry>
        <inputEntry id="ie_amount_4"><text>&lt;= 50000</text></inputEntry>
        <outputEntry id="oe_decision_4"><text>"approve"</text></outputEntry>
        <outputEntry id="oe_amount_4"><text>appraisedAmount * 0.8</text></outputEntry>
      </rule>

      <!-- Rule 5: large amount → reject -->
      <rule id="rule_large">
        <inputEntry id="ie_fraud_5"><text></text></inputEntry>
        <inputEntry id="ie_type_5"><text></text></inputEntry>
        <inputEntry id="ie_amount_5"><text></text></inputEntry>
        <outputEntry id="oe_decision_5"><text>"reject"</text></outputEntry>
        <outputEntry id="oe_amount_5"><text>0</text></outputEntry>
      </rule>

    </decisionTable>
  </decision>
</definitions>
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd examples/use-cases/insurance-claim && ./mvnw verify
```

Expected: BUILD SUCCESS; `Tests run: 2, Failures: 0` in failsafe output.

- [ ] **Step 5: Commit**

```bash
git add examples/use-cases/insurance-claim/src/main/resources/claim-settlement.dmn \
        examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim/InsuranceClaimIT.java
git commit -m "feat(insurance-claim): DMN claim-settlement (FIRST, FEEL) + evaluation tests"
```

---

## Task 3: Full BPMN process model + 4 delegate beans

**Files:**
- Replace: `examples/use-cases/insurance-claim/src/main/resources/insurance-claim.bpmn` (minimal stub → full model)
- Create: `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/RequestDocumentsDelegate.java`
- Create: `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/FraudCheckDelegate.java`
- Create: `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/AppraiseDamageDelegate.java`
- Create: `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/CloseIncompleteClaimDelegate.java`

**Interfaces:**
- Consumes: deployed `insurance-claim` process from Task 1, deployed `claim-settlement` DMN from Task 2.
- Produces: complete BPMN with event-based gateway, parallel gateway, message catch, timer catch, business rule task; four `JavaDelegate` Spring beans referenced by `delegateExpression`.

Process variable contract:
- `RequestDocumentsDelegate` reads: `claimNumber` (String), `policyNumber` (String) — logs only, sets nothing.
- `FraudCheckDelegate` reads: `estimatedAmount` (Double); sets: `fraudSuspected` (Boolean).
- `AppraiseDamageDelegate` reads: `estimatedAmount` (Double); sets: `appraisedAmount` (Double).
- `CloseIncompleteClaimDelegate` reads: `claimNumber` (String) — logs only, sets nothing.
- Business Rule Task: input variables `fraudSuspected`, `claimType`, `appraisedAmount`; result stored in `claimDecision` (type `singleResult` map).
- XOR gateway condition (approved flow): `${claimDecision.settlementDecision == "approve"}`.

- [ ] **Step 1: Replace `insurance-claim.bpmn` with the full model**

Replace the entire contents of `examples/use-cases/insurance-claim/src/main/resources/insurance-claim.bpmn`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:operaton="http://operaton.org/schema/1.0/bpmn"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  id="Definitions_InsuranceClaim"
                  targetNamespace="http://operaton.org/examples/insurance-claim">

  <bpmn:message id="Message_DocumentsReceived" name="documentsReceived"/>

  <bpmn:process id="insurance-claim"
                name="Insurance Claim"
                isExecutable="true"
                operaton:historyTimeToLive="P30D">

    <!-- ── Start ── -->
    <bpmn:startEvent id="StartEvent_ClaimSubmitted" name="Claim submitted">
      <bpmn:outgoing>Flow_Start_RequestDocs</bpmn:outgoing>
    </bpmn:startEvent>

    <!-- ── Request supporting documents ── -->
    <bpmn:serviceTask id="Task_RequestDocuments"
                      name="Request supporting documents"
                      operaton:delegateExpression="${requestDocumentsDelegate}">
      <bpmn:incoming>Flow_Start_RequestDocs</bpmn:incoming>
      <bpmn:outgoing>Flow_RequestDocs_Gateway</bpmn:outgoing>
    </bpmn:serviceTask>

    <!-- ── Event-based gateway ── -->
    <bpmn:eventBasedGateway id="Gateway_AwaitDocuments" name="Await documents">
      <bpmn:incoming>Flow_RequestDocs_Gateway</bpmn:incoming>
      <bpmn:outgoing>Flow_Gateway_Message</bpmn:outgoing>
      <bpmn:outgoing>Flow_Gateway_Timer</bpmn:outgoing>
    </bpmn:eventBasedGateway>

    <!-- ── Message: Documents received ── -->
    <bpmn:intermediateCatchEvent id="Event_DocumentsReceived" name="Documents received">
      <bpmn:incoming>Flow_Gateway_Message</bpmn:incoming>
      <bpmn:outgoing>Flow_Message_Split</bpmn:outgoing>
      <bpmn:messageEventDefinition id="MessageDef_DocumentsReceived"
                                   messageRef="Message_DocumentsReceived"/>
    </bpmn:intermediateCatchEvent>

    <!-- ── Timer: Submission deadline ── -->
    <bpmn:intermediateCatchEvent id="Event_DeadlineExpired" name="Submission deadline">
      <bpmn:incoming>Flow_Gateway_Timer</bpmn:incoming>
      <bpmn:outgoing>Flow_Timer_Close</bpmn:outgoing>
      <bpmn:timerEventDefinition id="TimerDef_Deadline">
        <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">${documentDeadline}</bpmn:timeDuration>
      </bpmn:timerEventDefinition>
    </bpmn:intermediateCatchEvent>

    <!-- ── Timeout path ── -->
    <bpmn:serviceTask id="Task_CloseIncompleteClaim"
                      name="Close incomplete claim"
                      operaton:delegateExpression="${closeIncompleteClaimDelegate}">
      <bpmn:incoming>Flow_Timer_Close</bpmn:incoming>
      <bpmn:outgoing>Flow_Close_EndClosed</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:endEvent id="EndEvent_ClaimClosed" name="Claim closed — documents not received">
      <bpmn:incoming>Flow_Close_EndClosed</bpmn:incoming>
    </bpmn:endEvent>

    <!-- ── Parallel gateway: AND-split ── -->
    <bpmn:parallelGateway id="Gateway_AssessmentSplit" name="Run assessments">
      <bpmn:incoming>Flow_Message_Split</bpmn:incoming>
      <bpmn:outgoing>Flow_Split_Fraud</bpmn:outgoing>
      <bpmn:outgoing>Flow_Split_Appraise</bpmn:outgoing>
    </bpmn:parallelGateway>

    <!-- ── Fraud check (branch 1) ── -->
    <bpmn:serviceTask id="Task_FraudCheck"
                      name="Fraud check"
                      operaton:delegateExpression="${fraudCheckDelegate}">
      <bpmn:incoming>Flow_Split_Fraud</bpmn:incoming>
      <bpmn:outgoing>Flow_Fraud_Join</bpmn:outgoing>
    </bpmn:serviceTask>

    <!-- ── Appraise damage (branch 2) ── -->
    <bpmn:serviceTask id="Task_AppraiseDamage"
                      name="Appraise damage"
                      operaton:delegateExpression="${appraiseDamageDelegate}">
      <bpmn:incoming>Flow_Split_Appraise</bpmn:incoming>
      <bpmn:outgoing>Flow_Appraise_Join</bpmn:outgoing>
    </bpmn:serviceTask>

    <!-- ── Parallel gateway: AND-join ── -->
    <bpmn:parallelGateway id="Gateway_AssessmentJoin">
      <bpmn:incoming>Flow_Fraud_Join</bpmn:incoming>
      <bpmn:incoming>Flow_Appraise_Join</bpmn:incoming>
      <bpmn:outgoing>Flow_Join_Settlement</bpmn:outgoing>
    </bpmn:parallelGateway>

    <!-- ── Business rule task: DMN settlement ── -->
    <bpmn:businessRuleTask id="Task_DetermineSettlement"
                           name="Determine settlement"
                           operaton:decisionRef="claim-settlement"
                           operaton:resultVariable="claimDecision"
                           operaton:mapDecisionResult="singleResult">
      <bpmn:incoming>Flow_Join_Settlement</bpmn:incoming>
      <bpmn:outgoing>Flow_Settlement_Approved</bpmn:outgoing>
    </bpmn:businessRuleTask>

    <!-- ── Exclusive gateway ── -->
    <bpmn:exclusiveGateway id="Gateway_Approved" name="Approved?" default="Flow_Approved_Rejected">
      <bpmn:incoming>Flow_Settlement_Approved</bpmn:incoming>
      <bpmn:outgoing>Flow_Approved_Settled</bpmn:outgoing>
      <bpmn:outgoing>Flow_Approved_Rejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <!-- ── End events ── -->
    <bpmn:endEvent id="EndEvent_ClaimSettled" name="Claim settled">
      <bpmn:incoming>Flow_Approved_Settled</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:endEvent id="EndEvent_ClaimRejected" name="Claim rejected">
      <bpmn:incoming>Flow_Approved_Rejected</bpmn:incoming>
    </bpmn:endEvent>

    <!-- ── Sequence flows ── -->
    <bpmn:sequenceFlow id="Flow_Start_RequestDocs"
                       sourceRef="StartEvent_ClaimSubmitted"
                       targetRef="Task_RequestDocuments"/>
    <bpmn:sequenceFlow id="Flow_RequestDocs_Gateway"
                       sourceRef="Task_RequestDocuments"
                       targetRef="Gateway_AwaitDocuments"/>
    <bpmn:sequenceFlow id="Flow_Gateway_Message"
                       name="Documents received"
                       sourceRef="Gateway_AwaitDocuments"
                       targetRef="Event_DocumentsReceived"/>
    <bpmn:sequenceFlow id="Flow_Gateway_Timer"
                       name="Submission deadline"
                       sourceRef="Gateway_AwaitDocuments"
                       targetRef="Event_DeadlineExpired"/>
    <bpmn:sequenceFlow id="Flow_Message_Split"
                       sourceRef="Event_DocumentsReceived"
                       targetRef="Gateway_AssessmentSplit"/>
    <bpmn:sequenceFlow id="Flow_Timer_Close"
                       sourceRef="Event_DeadlineExpired"
                       targetRef="Task_CloseIncompleteClaim"/>
    <bpmn:sequenceFlow id="Flow_Close_EndClosed"
                       sourceRef="Task_CloseIncompleteClaim"
                       targetRef="EndEvent_ClaimClosed"/>
    <bpmn:sequenceFlow id="Flow_Split_Fraud"
                       sourceRef="Gateway_AssessmentSplit"
                       targetRef="Task_FraudCheck"/>
    <bpmn:sequenceFlow id="Flow_Split_Appraise"
                       sourceRef="Gateway_AssessmentSplit"
                       targetRef="Task_AppraiseDamage"/>
    <bpmn:sequenceFlow id="Flow_Fraud_Join"
                       sourceRef="Task_FraudCheck"
                       targetRef="Gateway_AssessmentJoin"/>
    <bpmn:sequenceFlow id="Flow_Appraise_Join"
                       sourceRef="Task_AppraiseDamage"
                       targetRef="Gateway_AssessmentJoin"/>
    <bpmn:sequenceFlow id="Flow_Join_Settlement"
                       sourceRef="Gateway_AssessmentJoin"
                       targetRef="Task_DetermineSettlement"/>
    <bpmn:sequenceFlow id="Flow_Settlement_Approved"
                       sourceRef="Task_DetermineSettlement"
                       targetRef="Gateway_Approved"/>
    <bpmn:sequenceFlow id="Flow_Approved_Settled"
                       name="approve"
                       sourceRef="Gateway_Approved"
                       targetRef="EndEvent_ClaimSettled">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${claimDecision.settlementDecision == "approve"}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Approved_Rejected"
                       name="otherwise"
                       sourceRef="Gateway_Approved"
                       targetRef="EndEvent_ClaimRejected"/>
  </bpmn:process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="insurance-claim">

      <!-- Start -->
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_ClaimSubmitted">
        <dc:Bounds x="152" y="92" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="134" y="135" width="73" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Request documents -->
      <bpmndi:BPMNShape id="Shape_RequestDocs" bpmnElement="Task_RequestDocuments">
        <dc:Bounds x="240" y="70" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- Event-based gateway -->
      <bpmndi:BPMNShape id="Shape_EBG" bpmnElement="Gateway_AwaitDocuments" isMarkerVisible="true">
        <dc:Bounds x="395" y="85" width="50" height="50"/>
        <bpmndi:BPMNLabel><dc:Bounds x="378" y="142" width="85" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Message catch -->
      <bpmndi:BPMNShape id="Shape_MsgCatch" bpmnElement="Event_DocumentsReceived">
        <dc:Bounds x="502" y="52" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="480" y="95" width="82" height="27"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Timer catch -->
      <bpmndi:BPMNShape id="Shape_TimerCatch" bpmnElement="Event_DeadlineExpired">
        <dc:Bounds x="502" y="152" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="478" y="195" width="86" height="27"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Parallel split -->
      <bpmndi:BPMNShape id="Shape_Split" bpmnElement="Gateway_AssessmentSplit">
        <dc:Bounds x="595" y="45" width="50" height="50"/>
        <bpmndi:BPMNLabel><dc:Bounds x="575" y="102" width="92" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- FraudCheck -->
      <bpmndi:BPMNShape id="Shape_FraudCheck" bpmnElement="Task_FraudCheck">
        <dc:Bounds x="710" y="20" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- AppraiseDamage -->
      <bpmndi:BPMNShape id="Shape_Appraise" bpmnElement="Task_AppraiseDamage">
        <dc:Bounds x="710" y="120" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- Parallel join -->
      <bpmndi:BPMNShape id="Shape_Join" bpmnElement="Gateway_AssessmentJoin">
        <dc:Bounds x="875" y="45" width="50" height="50"/>
      </bpmndi:BPMNShape>

      <!-- Business rule task -->
      <bpmndi:BPMNShape id="Shape_BRT" bpmnElement="Task_DetermineSettlement">
        <dc:Bounds x="990" y="30" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- Exclusive gateway -->
      <bpmndi:BPMNShape id="Shape_XOR" bpmnElement="Gateway_Approved" isMarkerVisible="true">
        <dc:Bounds x="1155" y="45" width="50" height="50"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1153" y="102" width="55" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- End: Claim settled -->
      <bpmndi:BPMNShape id="Shape_EndSettled" bpmnElement="EndEvent_ClaimSettled">
        <dc:Bounds x="1272" y="22" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1254" y="65" width="72" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- End: Claim rejected -->
      <bpmndi:BPMNShape id="Shape_EndRejected" bpmnElement="EndEvent_ClaimRejected">
        <dc:Bounds x="1272" y="122" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1254" y="165" width="73" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Close claim -->
      <bpmndi:BPMNShape id="Shape_Close" bpmnElement="Task_CloseIncompleteClaim">
        <dc:Bounds x="595" y="260" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- End: Claim closed -->
      <bpmndi:BPMNShape id="Shape_EndClosed" bpmnElement="EndEvent_ClaimClosed">
        <dc:Bounds x="757" y="282" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="730" y="325" width="91" height="40"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Edges -->
      <bpmndi:BPMNEdge id="Edge_Start_Request" bpmnElement="Flow_Start_RequestDocs">
        <di:waypoint x="188" y="110"/><di:waypoint x="240" y="110"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Request_GW" bpmnElement="Flow_RequestDocs_Gateway">
        <di:waypoint x="340" y="110"/><di:waypoint x="395" y="110"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_GW_Msg" bpmnElement="Flow_Gateway_Message">
        <di:waypoint x="420" y="85"/><di:waypoint x="420" y="70"/><di:waypoint x="502" y="70"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_GW_Timer" bpmnElement="Flow_Gateway_Timer">
        <di:waypoint x="420" y="135"/><di:waypoint x="420" y="170"/><di:waypoint x="502" y="170"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Msg_Split" bpmnElement="Flow_Message_Split">
        <di:waypoint x="538" y="70"/><di:waypoint x="620" y="70"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Timer_Close" bpmnElement="Flow_Timer_Close">
        <di:waypoint x="520" y="188"/><di:waypoint x="520" y="300"/><di:waypoint x="595" y="300"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Close_EndClosed" bpmnElement="Flow_Close_EndClosed">
        <di:waypoint x="695" y="300"/><di:waypoint x="757" y="300"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Split_Fraud" bpmnElement="Flow_Split_Fraud">
        <di:waypoint x="620" y="45"/><di:waypoint x="620" y="30"/><di:waypoint x="710" y="60"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Split_Appraise" bpmnElement="Flow_Split_Appraise">
        <di:waypoint x="620" y="95"/><di:waypoint x="620" y="160"/><di:waypoint x="710" y="160"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Fraud_Join" bpmnElement="Flow_Fraud_Join">
        <di:waypoint x="810" y="60"/><di:waypoint x="875" y="70"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Appraise_Join" bpmnElement="Flow_Appraise_Join">
        <di:waypoint x="810" y="160"/><di:waypoint x="875" y="70"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Join_Settlement" bpmnElement="Flow_Join_Settlement">
        <di:waypoint x="925" y="70"/><di:waypoint x="990" y="70"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Settlement_XOR" bpmnElement="Flow_Settlement_Approved">
        <di:waypoint x="1090" y="70"/><di:waypoint x="1155" y="70"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_XOR_Settled" bpmnElement="Flow_Approved_Settled">
        <di:waypoint x="1180" y="45"/><di:waypoint x="1180" y="40"/><di:waypoint x="1272" y="40"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1202" y="22" width="40" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_XOR_Rejected" bpmnElement="Flow_Approved_Rejected">
        <di:waypoint x="1180" y="95"/><di:waypoint x="1180" y="140"/><di:waypoint x="1272" y="140"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1197" y="115" width="56" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

- [ ] **Step 2: Create `RequestDocumentsDelegate.java`**

Create `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/RequestDocumentsDelegate.java`:

```java
package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("requestDocumentsDelegate")
public class RequestDocumentsDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(RequestDocumentsDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String claimNumber = (String) execution.getVariable("claimNumber");
        String policyNumber = (String) execution.getVariable("policyNumber");
        log.info("Requesting documents for claim {} from policy {}", claimNumber, policyNumber);
    }
}
```

- [ ] **Step 3: Create `FraudCheckDelegate.java`**

Create `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/FraudCheckDelegate.java`:

```java
package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("fraudCheckDelegate")
public class FraudCheckDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(FraudCheckDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Double estimatedAmount = (Double) execution.getVariable("estimatedAmount");
        boolean fraudSuspected = estimatedAmount != null && estimatedAmount > 100_000;
        execution.setVariable("fraudSuspected", fraudSuspected);
        log.info("Fraud check for claim {}: estimatedAmount={}, fraudSuspected={}",
            execution.getBusinessKey(), estimatedAmount, fraudSuspected);
    }
}
```

- [ ] **Step 4: Create `AppraiseDamageDelegate.java`**

Create `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/AppraiseDamageDelegate.java`:

```java
package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("appraiseDamageDelegate")
public class AppraiseDamageDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(AppraiseDamageDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Double estimatedAmount = (Double) execution.getVariable("estimatedAmount");
        double appraisedAmount = estimatedAmount != null ? estimatedAmount * 0.9 : 0.0;
        execution.setVariable("appraisedAmount", appraisedAmount);
        log.info("Damage appraisal for claim {}: estimatedAmount={}, appraisedAmount={}",
            execution.getBusinessKey(), estimatedAmount, appraisedAmount);
    }
}
```

- [ ] **Step 5: Create `CloseIncompleteClaimDelegate.java`**

Create `examples/use-cases/insurance-claim/src/main/java/org/operaton/examples/insuranceclaim/CloseIncompleteClaimDelegate.java`:

```java
package org.operaton.examples.insuranceclaim;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("closeIncompleteClaimDelegate")
public class CloseIncompleteClaimDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CloseIncompleteClaimDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String claimNumber = (String) execution.getVariable("claimNumber");
        log.info("Closing claim {} — no documents received within deadline", claimNumber);
    }
}
```

- [ ] **Step 6: Run tests — verify they pass**

```bash
cd examples/use-cases/insurance-claim && ./mvnw verify
```

Expected: BUILD SUCCESS; `Tests run: 2, Failures: 0` (smoke test + DMN test). If the startup fails with `Could not find bean 'requestDocumentsDelegate'`, check that the delegate classes have the correct `@Component` bean names and are in the `org.operaton.examples.insuranceclaim` package (auto-scanned by `@SpringBootApplication`).

- [ ] **Step 7: Commit**

```bash
git add examples/use-cases/insurance-claim/src/main/resources/insurance-claim.bpmn \
        examples/use-cases/insurance-claim/src/main/java/
git commit -m "feat(insurance-claim): full BPMN model + 4 delegate beans"
```

---

## Task 4: End-to-end integration tests

**Files:**
- Modify: `examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim/InsuranceClaimIT.java`

**Interfaces:**
- Consumes: `insurance-claim` process (Task 3), `claim-settlement` DMN (Task 2), all four delegates (Task 3).
- Produces: three e2e test methods: happy path (collision → settled), reject path (flood → rejected), timeout path (timer → closed).

The three test scenarios:
1. **Happy**: `claimType="collision"`, `estimatedAmount=800.0`, correlate message → `EndEvent_ClaimSettled`, `appraisedAmount=720.0`.
2. **Reject**: `claimType="flood"`, `estimatedAmount=5000.0`, correlate message → `EndEvent_ClaimRejected`.
3. **Timeout**: `documentDeadline="PT3S"`, no message → `EndEvent_ClaimClosed` (wait up to 30s).

- [ ] **Step 1: Write the three failing e2e tests**

Add the following three methods to `InsuranceClaimIT.java` (after `settlementDecisionMapsCorrectly`). Also add the required imports at the top of the file:

New imports to add:

```java
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
```

New test methods:

```java
    @Test
    void happyPath_smallCollisionClaim_settlesWithAppraisedAmount() {
        var pi = runtimeService.startProcessInstanceByKey(
            "insurance-claim",
            "CLM-001",
            Variables.createVariables()
                .putValue("claimNumber", "CLM-001")
                .putValue("policyNumber", "POL-42")
                .putValue("claimType", "collision")
                .putValue("estimatedAmount", 800.0)
                .putValue("documentDeadline", "P14D"));

        runtimeService.correlateMessage("documentsReceived", "CLM-001", Map.of());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ClaimSettled");
        });

        var appraisedAmount = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("appraisedAmount").singleResult();
        assertThat(appraisedAmount).isNotNull();
        assertThat((Double) appraisedAmount.getValue()).isEqualTo(720.0);
    }

    @Test
    void rejectPath_floodClaim_isRejectedRegardlessOfAmount() {
        var pi = runtimeService.startProcessInstanceByKey(
            "insurance-claim",
            "CLM-002",
            Variables.createVariables()
                .putValue("claimNumber", "CLM-002")
                .putValue("policyNumber", "POL-43")
                .putValue("claimType", "flood")
                .putValue("estimatedAmount", 5000.0)
                .putValue("documentDeadline", "P14D"));

        runtimeService.correlateMessage("documentsReceived", "CLM-002", Map.of());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ClaimRejected");
        });
    }

    @Test
    void timeoutPath_noDocumentsReceived_closesClaimAfterDeadline() {
        var pi = runtimeService.startProcessInstanceByKey(
            "insurance-claim",
            "CLM-003",
            Variables.createVariables()
                .putValue("claimNumber", "CLM-003")
                .putValue("policyNumber", "POL-44")
                .putValue("claimType", "collision")
                .putValue("estimatedAmount", 500.0)
                .putValue("documentDeadline", "PT3S"));

        // Do NOT send the message — the timer (PT3S) should fire instead

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_ClaimClosed");
        });
    }
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd examples/use-cases/insurance-claim && ./mvnw verify
```

Expected: The new e2e tests will fail with `ConditionTimeoutException` (process does not complete) because the BPMN is not yet deployed with the full flow. If instead you see compilation errors, fix import issues — ensure `Map`, `Duration`, `await`, `Variables`, `HistoricProcessInstance` are all imported.

If tests time out (rather than fail fast), you may skip this step and proceed to verify after Task 3's BPMN is in place.

- [ ] **Step 3: Run full test suite — verify all 5 tests pass**

```bash
cd examples/use-cases/insurance-claim && ./mvnw verify
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`. All three e2e paths complete within the 30s Awaitility timeout.

**Troubleshooting:**
- If `happyPath` fails: check `appraisedAmount` delegate sets the variable, DMN rule 3 fires (720 <= 1000 → full amount), XOR gateway routes to `EndEvent_ClaimSettled`.
- If `rejectPath` fails: check DMN rule 2 fires for `claimType="flood"`, routes to `EndEvent_ClaimRejected`.
- If `timeoutPath` times out: check the timer duration expression `${documentDeadline}` is in the BPMN's `timeDuration` element; confirm `PT3S` is ISO-8601 valid; check job executor is running (it's enabled by default in Spring Boot tests unless explicitly disabled).

- [ ] **Step 4: Commit**

```bash
git add examples/use-cases/insurance-claim/src/test/java/org/operaton/examples/insuranceclaim/InsuranceClaimIT.java
git commit -m "test(insurance-claim): e2e IT — happy, reject, timeout paths"
```

---

## Task 5: Documentation, BPMN PNG, registry entry, root README concept table

**Files:**
- Create: `examples/use-cases/insurance-claim/README.md`
- Create: `examples/use-cases/insurance-claim/src/main/resources/insurance-claim.png` (rendered)
- Modify: `.operaton-starter.yml` (repo root) — add insurance-claim entry after order-fulfillment
- Modify: `README.md` (repo root) — add BPMN concept mapping section

**Interfaces:**
- Consumes: final `insurance-claim.bpmn` from Task 3, all prior tasks completed.
- Produces: complete documentation per EXAMPLE_STANDARDS.md §8; registry entry; concept table in root README.

- [ ] **Step 1: Render the BPMN to PNG**

Run from the `operaton-examples/` repo root:

```bash
./scripts/render-bpmn.sh examples/use-cases/insurance-claim
```

Expected: `insurance-claim.png` created alongside the `.bpmn` file. If `bpmn-to-image` is not installed: `npm install -g bpmn-to-image`.

- [ ] **Step 2: Create `README.md`**

Create `examples/use-cases/insurance-claim/README.md`:

```markdown
# Insurance Claim

Demonstrates the **event-based gateway** — a race between a message event (documents
received) and a timer event (submission deadline) — together with a **parallel gateway**
for concurrent fraud check and damage appraisal.

## What you will learn

- How to model an **event-based gateway** that races a message catch against a timer catch
- How to use a **parallel gateway** (AND-split / AND-join) for concurrent service calls
- How to correlate a message to a running process instance by **business key**
- How to evaluate a **DMN decision** from a business rule task using the FIRST hit policy with FEEL output expressions
- How to drive the ISO-8601 timer duration from a **process variable** (`${documentDeadline}`)

## Process model

![Process diagram](src/main/resources/insurance-claim.png)

## Prerequisites

- JDK 21
- Docker (for local PostgreSQL)

## Run it

Start the database:

```bash
docker compose up -d
```

Run the application:

```bash
./mvnw spring-boot:run
# or
./gradlew bootRun
```

Open Cockpit / Tasklist: http://localhost:8080  
Credentials: `demo` / `demo`

## Walk through it

**Happy path — collision claim, documents submitted on time:**

```bash
# 1. Start a claim
curl -s -X POST http://localhost:8080/engine-rest/process-definition/key/insurance-claim/start \
  -H "Content-Type: application/json" \
  -d '{
    "businessKey": "CLM-2024-001",
    "variables": {
      "claimNumber":    { "value": "CLM-2024-001", "type": "String" },
      "policyNumber":   { "value": "POL-999",      "type": "String" },
      "claimType":      { "value": "collision",    "type": "String" },
      "estimatedAmount":{ "value": 800.0,          "type": "Double" },
      "documentDeadline":{ "value": "P14D",        "type": "String" }
    }
  }'

# 2. Submit the documents (correlate message by business key)
curl -s -X POST http://localhost:8080/engine-rest/message \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "documentsReceived",
    "businessKey": "CLM-2024-001"
  }'

# 3. Check the result in Cockpit — the instance should be completed at "Claim settled"
```

**Timeout path — no documents submitted:**

Start a claim with `documentDeadline: "PT30S"` and do NOT send the message.
After 30 seconds the process closes automatically at "Claim closed — documents not received".

**Reject path — flood damage:**

Start a claim with `claimType: "flood"`, submit documents, and observe that the
DMN decision routes to "Claim rejected" regardless of the amount.

## How it works

The process starts at `StartEvent_ClaimSubmitted` and immediately requests documents
via `RequestDocumentsDelegate` (logs the request — in a real system, sends an email).

The **event-based gateway** (`Gateway_AwaitDocuments`) opens two competing subscriptions:
a message subscription for `documentsReceived` and a timer subscription for the duration
in `${documentDeadline}`. Whichever fires first wins; the other is automatically cancelled
by the engine.

If documents arrive first, the **parallel gateway** (`Gateway_AssessmentSplit`) fans out
to two concurrent service tasks:
- `FraudCheckDelegate` — sets `fraudSuspected = (estimatedAmount > 100,000)`
- `AppraiseDamageDelegate` — sets `appraisedAmount = estimatedAmount × 0.9`

The **AND-join** (`Gateway_AssessmentJoin`) waits for both before the business rule task
evaluates `claim-settlement.dmn`. The DMN uses FIRST hit policy: fraud or flood → reject;
small amounts → approve full appraised; medium → approve at 80%; large → reject.

If the deadline fires first, `CloseIncompleteClaimDelegate` logs the closure and the
process ends at `EndEvent_ClaimClosed`.

## Run the tests

```bash
./mvnw verify
# or
./gradlew build
```

The integration tests (`InsuranceClaimIT`) run three scenarios against Testcontainers PostgreSQL:
happy path (collision/800 → settled), reject path (flood → rejected), and timeout path
(PT3S deadline, no message → closed).
```

- [ ] **Step 3: Add registry entry to `.operaton-starter.yml`**

Append the following entry to `.operaton-starter.yml` (repo root), after the `order-fulfillment` block:

```yaml
  - id: insurance-claim
    title: "Use Case — Insurance Claim"
    icon: "🏥"
    path: examples/use-cases/insurance-claim
    shortDescription: >
      Insurance damage claim with event-based gateway racing message vs timer,
      parallel fraud check and damage appraisal, and DMN settlement decision.
    longDescription: |
      An insurance company processes a damage claim: requests supporting documents,
      then races a message event (documents received) against a timer (submission deadline)
      via an event-based gateway.

      If documents arrive in time, two parallel service tasks run concurrently — a fraud
      check and a damage appraisal — before a DMN table determines settlement.

      - **Event-based gateway** — first coverage in the catalog; models a race between a
        message correlation and a timer expiry
      - **Parallel gateway (AND-split / AND-join)** — concurrent fraud check and damage
        appraisal; also first in the catalog
      - **Message correlation by business key** — `correlateMessage("documentsReceived", claimNumber)`
      - **DMN FIRST hit policy with FEEL outputs** — `appraisedAmount * 0.8` in output entries
      - **ISO-8601 timer from process variable** — `${documentDeadline}` in `timeDuration`
    buildSystem: maven
    runtime: spring-boot
    operatonVersion: "2.1.1"
    javaVersion: "21"
    complexity: intermediate
    tags:
      - { label: "Insurance", category: concept }
      - { label: "Event-Based GW", category: concept }
      - { label: "Parallel GW", category: concept }
      - { label: "DMN FIRST", category: concept }
    integrations: [postgres]
    bpmnConcepts: [event-based-gateway, parallel-gateway, message-intermediate-catch, timer-intermediate-catch, business-rule-task, exclusive-gateway]
    requires: "Java 21+, Docker"
    authors:
      - { name: "Karsten Thoms", url: "https://github.com/kthoms" }
    license: "Apache-2.0"
    screenshots:
      - examples/use-cases/insurance-claim/src/main/resources/insurance-claim.png
    documentationUrl: "https://github.com/kthoms/operaton-examples/blob/main/examples/use-cases/insurance-claim/README.md"
    lastUpdated: "2026-06-22"
```

- [ ] **Step 4: Add BPMN concept mapping section to root `README.md`**

Open the root `README.md`. Find the examples table. After it, add the following new section. If a "BPMN Concept Reference" section already exists, replace it entirely. If it does not exist, append it at the end of the file.

```markdown
## BPMN Concept Reference

Quick lookup: which example demonstrates each BPMN construct.

### BPMN Concepts

| BPMN Concept | Example(s) | Notes |
|---|---|---|
| Service task | getting-started, service-tasks | Java delegate, Spring bean, expression |
| User task | user-task-forms | Forms, candidate groups |
| Script task | service-tasks | JavaScript / Groovy inline |
| Business rule task (DMN) | dmn-decision, insurance-claim | FEEL expressions, hit policies |
| Exclusive gateway (XOR) | getting-started, dmn-decision | Default flow, condition expressions |
| **Parallel gateway (AND)** | **insurance-claim** | AND-split / AND-join, concurrent branches |
| **Event-based gateway** | **insurance-claim** | Race between message and timer |
| Inclusive gateway (OR) | inclusive-gateway | OR-split / OR-join |
| Message start event | message-events | Start by message correlation |
| Timer start event | timer-events | Cron, cycle, duration |
| Message intermediate catch | message-events, insurance-claim | Correlation by business key |
| Timer intermediate catch | timer-events, insurance-claim | ISO-8601 duration variable |
| Signal intermediate catch/throw | signal-events | Broadcast signal |
| Error boundary event | error-compensation | Interrupting and non-interrupting |
| Compensation | error-compensation | Compensation boundary + handler |
| Multi-instance | multi-instance | Sequential and parallel sub-tasks |
| Call activity | call-activity | Sub-process reuse across definitions |
| Event sub-process | event-subprocess | Error- and message-triggered |
| External task | external-task-worker | Worker API, long polling |
| Async continuation | async-continuation | `asyncBefore`, exclusive job lock |

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
| Spring Boot (embedded) | getting-started, all use-cases, approval-sla-metrics, … |
| Quarkus (embedded) | runtime-quarkus |
| Tomcat (shared engine) | distribution-tomcat |
| WildFly (shared engine) | distribution-wildfly |
| Flowset Control + SSO (Keycloak) | operaton-example-projects / operaton-flowset-sso |
```

- [ ] **Step 5: Run full tests one last time**

```bash
cd examples/use-cases/insurance-claim && ./mvnw verify
```

Expected: BUILD SUCCESS; all 5 tests pass.

- [ ] **Step 6: Commit**

```bash
git add examples/use-cases/insurance-claim/README.md \
        examples/use-cases/insurance-claim/src/main/resources/insurance-claim.png \
        .operaton-starter.yml \
        README.md
git commit -m "docs(insurance-claim): README, BPMN PNG, registry entry, root concept table"
```
