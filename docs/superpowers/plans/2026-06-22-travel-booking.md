# Travel Booking SAGA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `uc-07-travel-booking` — a BPMN transaction subprocess (flight + hotel + car booking as a SAGA) where payment decline triggers a cancel end event, which auto-compensates all reservations via the cancel boundary event.

**Architecture:** Spring Boot 4.1.0 embedded Operaton engine. The entire process is synchronous service tasks inside a `<bpmn:transaction>` subprocess; cancel end event → cancel boundary event → auto-compensation → "Trip cancelled" end. No DMN, no user tasks, no external services.

**Tech Stack:** Spring Boot 4.1.0, Operaton 2.1.1, Testcontainers 2.0.5 (PostgreSQL), Awaitility (from Spring Boot BOM), Java 21, Maven + Gradle dual build.

## Global Constraints

- Java 21, Spring Boot **4.1.0**, Operaton **2.1.1** — identical in `pom.xml` and `build.gradle.kts`
- Maven artifactId: `uc-07-travel-booking`; Java package: `org.operaton.examples.travelbooking`
- BPMN process id: `travel-booking`; file: `travel-booking.bpmn`
- Parent pom: `operaton-examples-aggregate`, `relativePath ../../../pom.xml`
- Testcontainers BOM 2.0.5 at `testImplementation` scope in Gradle; `test` scope in Maven BOM import
- TC 2.x package: `org.testcontainers.postgresql.PostgreSQLContainer`
- No `operaton:class` — use `operaton:delegateExpression="${beanName}"` on every service task
- `operaton:historyTimeToLive="P30D"` on the process element
- No H2, no `Thread.sleep`, no stub delegates (all delegates fully implemented)
- `maven-failsafe-plugin` declared in `pom.xml` so `*IT` runs under `./mvnw verify`
- docker-compose: `postgres:16-alpine`, host port 5432, healthcheck with `start_period: 5s`
- Admin user `demo/demo`; no additional seeded users (no user tasks)

---

### Task 1: Module scaffold + aggregator registration + smoke IT

**Files:**
- Create: `examples/use-cases/travel-booking/pom.xml`
- Create: `examples/use-cases/travel-booking/build.gradle.kts`
- Create: `examples/use-cases/travel-booking/settings.gradle.kts`
- Create: `examples/use-cases/travel-booking/docker-compose.yml`
- Create: `examples/use-cases/travel-booking/src/main/resources/application.yaml`
- Create: `examples/use-cases/travel-booking/src/main/java/org/operaton/examples/travelbooking/TravelBookingApplication.java`
- Create: `examples/use-cases/travel-booking/src/main/resources/travel-booking.bpmn` (minimal stub)
- Create: `examples/use-cases/travel-booking/src/test/java/org/operaton/examples/travelbooking/TravelBookingIT.java` (smoke only)
- Modify: `pom.xml` (root) — add `<module>examples/use-cases/travel-booking</module>`
- Modify: `settings.gradle.kts` (root) — add include + projectDir

**Interfaces:**
- Produces: module compiles, `./mvnw verify` and `./gradlew build` pass; smoke IT deploys `travel-booking` process definition

- [ ] **Step 1: Copy Maven wrapper from insurance-claim**

```bash
cp -r examples/use-cases/insurance-claim/.mvn examples/use-cases/travel-booking/.mvn
cp examples/use-cases/insurance-claim/mvnw examples/use-cases/travel-booking/mvnw
cp examples/use-cases/insurance-claim/mvnw.cmd examples/use-cases/travel-booking/mvnw.cmd
chmod +x examples/use-cases/travel-booking/mvnw
```

- [ ] **Step 2: Copy Gradle wrapper from insurance-claim**

```bash
cp -r examples/use-cases/insurance-claim/gradle examples/use-cases/travel-booking/gradle
cp examples/use-cases/insurance-claim/gradlew examples/use-cases/travel-booking/gradlew
cp examples/use-cases/insurance-claim/gradlew.bat examples/use-cases/travel-booking/gradlew.bat
chmod +x examples/use-cases/travel-booking/gradlew
```

- [ ] **Step 3: Create `pom.xml`**

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
  <artifactId>uc-07-travel-booking</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>Operaton Example: Travel Booking SAGA</name>

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
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))

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

```kotlin
rootProject.name = "uc-07-travel-booking"
```

- [ ] **Step 6: Create `docker-compose.yml`**

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
      start_period: 5s
```

- [ ] **Step 7: Create `src/main/resources/application.yaml`**

```yaml
spring:
  application:
    name: travel-booking
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

- [ ] **Step 8: Create `TravelBookingApplication.java`**

```java
package org.operaton.examples.travelbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TravelBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelBookingApplication.class, args);
    }
}
```

- [ ] **Step 9: Create minimal stub BPMN `travel-booking.bpmn`**

This BPMN is a placeholder so the smoke test can verify deployment. Task 2 replaces it with the full model.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:operaton="http://operaton.org/schema/1.0/bpmn"
                  id="Definitions_TravelBooking"
                  targetNamespace="http://operaton.org/examples/travel-booking">

  <bpmn:process id="travel-booking"
                name="Travel Booking"
                isExecutable="true"
                operaton:historyTimeToLive="P30D">

    <bpmn:startEvent id="StartEvent_1" name="Trip requested">
      <bpmn:outgoing>Flow_Start_End</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:endEvent id="EndEvent_1" name="Trip booked">
      <bpmn:incoming>Flow_Start_End</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="Flow_Start_End" sourceRef="StartEvent_1" targetRef="EndEvent_1"/>

  </bpmn:process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="travel-booking">
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="222" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_End" bpmnElement="EndEvent_1">
        <dc:Bounds x="252" y="222" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Edge_Start_End" bpmnElement="Flow_Start_End">
        <di:waypoint xmlns:di="http://www.omg.org/spec/DD/20100524/DI" x="188" y="240"/>
        <di:waypoint xmlns:di="http://www.omg.org/spec/DD/20100524/DI" x="252" y="240"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

- [ ] **Step 10: Create smoke `TravelBookingIT.java`**

```java
package org.operaton.examples.travelbooking;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TravelBookingIT {

    @Container
    @ServiceConnection
    @SuppressWarnings("rawtypes")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ProcessEngine processEngine;

    @Test
    void processDefinitionIsDeployed() {
        assertThat(processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey("travel-booking")
            .count()).isEqualTo(1);
    }
}
```

- [ ] **Step 11: Register in root `pom.xml`**

Add inside `<modules>`, after the `insurance-claim` line:

```xml
    <module>examples/use-cases/travel-booking</module>
```

- [ ] **Step 12: Register in root `settings.gradle.kts`**

In the `include(...)` block, add after `"examples:use-cases:insurance-claim"`:

```kotlin
  "examples:use-cases:travel-booking",
```

After the last `project(...)` line, add:

```kotlin
project(":examples:use-cases:travel-booking").projectDir = file("examples/use-cases/travel-booking")
```

- [ ] **Step 13: Verify smoke test passes**

```bash
cd examples/use-cases/travel-booking
./mvnw verify
```

Expected: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 14: Commit**

```bash
git add examples/use-cases/travel-booking pom.xml settings.gradle.kts
git commit -m "feat(travel-booking): module scaffold, dual build, smoke IT"
```

---

### Task 2: Full BPMN model + 9 delegates

**Files:**
- Replace: `examples/use-cases/travel-booking/src/main/resources/travel-booking.bpmn`
- Create: `src/main/java/org/operaton/examples/travelbooking/ReserveFlightDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/ReserveHotelDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/ReserveCarDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/ChargePaymentDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/CancelFlightDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/CancelHotelDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/CancelCarDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/SendConfirmationDelegate.java`
- Create: `src/main/java/org/operaton/examples/travelbooking/NotifyCancellationDelegate.java`

**Interfaces:**
- Consumes: module scaffold from Task 1
- Produces: fully executable BPMN; `smoke IT` from Task 1 still passes; delegates are Spring beans referenced by `delegateExpression`

- [ ] **Step 1: Replace `travel-booking.bpmn` with full model**

The BPMN models a transaction subprocess "Book trip" containing: sequential reservations (flight → hotel → car → payment charge), a payment gateway, a cancel end event (payment declined), a normal end event (payment approved). Compensation boundary events are attached to each reservation task and wired to compensation handler tasks. A cancel boundary event on the outer transaction element routes to the "Trip cancelled" end event.

Key element IDs used by Task 3 integration tests:
- Process end events: `EndEvent_TripBooked`, `EndEvent_TripCancelled`
- Tasks: `Task_ReserveFlight`, `Task_ReserveHotel`, `Task_ReserveCar`, `Task_ChargePayment`
- Transaction: `Transaction_BookTrip`
- Cancel boundary on transaction: `Boundary_TransactionCancelled`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:operaton="http://operaton.org/schema/1.0/bpmn"
                  id="Definitions_TravelBooking"
                  targetNamespace="http://operaton.org/examples/travel-booking">

  <bpmn:process id="travel-booking"
                name="Travel Booking"
                isExecutable="true"
                operaton:historyTimeToLive="P30D">

    <!-- ── Outer process start ── -->
    <bpmn:startEvent id="StartEvent_TripRequested" name="Trip requested">
      <bpmn:outgoing>Flow_Start_Transaction</bpmn:outgoing>
    </bpmn:startEvent>

    <!-- ── Transaction subprocess ── -->
    <bpmn:transaction id="Transaction_BookTrip" name="Book trip">
      <bpmn:incoming>Flow_Start_Transaction</bpmn:incoming>
      <bpmn:outgoing>Flow_Transaction_Confirm</bpmn:outgoing>

      <!-- Inner start -->
      <bpmn:startEvent id="StartEvent_TxBegin" name="Booking started">
        <bpmn:outgoing>Flow_TxStart_Flight</bpmn:outgoing>
      </bpmn:startEvent>

      <!-- Reserve flight -->
      <bpmn:serviceTask id="Task_ReserveFlight" name="Reserve flight"
                        operaton:delegateExpression="${reserveFlightDelegate}">
        <bpmn:incoming>Flow_TxStart_Flight</bpmn:incoming>
        <bpmn:outgoing>Flow_Flight_Hotel</bpmn:outgoing>
      </bpmn:serviceTask>

      <bpmn:boundaryEvent id="Boundary_CompFlight" attachedToRef="Task_ReserveFlight" cancelActivity="false">
        <bpmn:compensateEventDefinition id="CompDef_Flight" waitForCompletion="true"/>
      </bpmn:boundaryEvent>

      <bpmn:serviceTask id="Task_CancelFlight" name="Cancel flight"
                        isForCompensation="true"
                        operaton:delegateExpression="${cancelFlightDelegate}"/>

      <!-- Reserve hotel -->
      <bpmn:serviceTask id="Task_ReserveHotel" name="Reserve hotel"
                        operaton:delegateExpression="${reserveHotelDelegate}">
        <bpmn:incoming>Flow_Flight_Hotel</bpmn:incoming>
        <bpmn:outgoing>Flow_Hotel_Car</bpmn:outgoing>
      </bpmn:serviceTask>

      <bpmn:boundaryEvent id="Boundary_CompHotel" attachedToRef="Task_ReserveHotel" cancelActivity="false">
        <bpmn:compensateEventDefinition id="CompDef_Hotel" waitForCompletion="true"/>
      </bpmn:boundaryEvent>

      <bpmn:serviceTask id="Task_CancelHotel" name="Cancel hotel"
                        isForCompensation="true"
                        operaton:delegateExpression="${cancelHotelDelegate}"/>

      <!-- Reserve car -->
      <bpmn:serviceTask id="Task_ReserveCar" name="Reserve car"
                        operaton:delegateExpression="${reserveCarDelegate}">
        <bpmn:incoming>Flow_Hotel_Car</bpmn:incoming>
        <bpmn:outgoing>Flow_Car_Payment</bpmn:outgoing>
      </bpmn:serviceTask>

      <bpmn:boundaryEvent id="Boundary_CompCar" attachedToRef="Task_ReserveCar" cancelActivity="false">
        <bpmn:compensateEventDefinition id="CompDef_Car" waitForCompletion="true"/>
      </bpmn:boundaryEvent>

      <bpmn:serviceTask id="Task_CancelCar" name="Cancel car"
                        isForCompensation="true"
                        operaton:delegateExpression="${cancelCarDelegate}"/>

      <!-- Charge payment -->
      <bpmn:serviceTask id="Task_ChargePayment" name="Charge payment"
                        operaton:delegateExpression="${chargePaymentDelegate}">
        <bpmn:incoming>Flow_Car_Payment</bpmn:incoming>
        <bpmn:outgoing>Flow_Payment_GW</bpmn:outgoing>
      </bpmn:serviceTask>

      <!-- Payment gateway -->
      <bpmn:exclusiveGateway id="Gateway_PaymentApproved" name="Payment approved?" default="Flow_GW_Declined">
        <bpmn:incoming>Flow_Payment_GW</bpmn:incoming>
        <bpmn:outgoing>Flow_GW_Approved</bpmn:outgoing>
        <bpmn:outgoing>Flow_GW_Declined</bpmn:outgoing>
      </bpmn:exclusiveGateway>

      <!-- Normal end (transaction succeeds) -->
      <bpmn:endEvent id="EndEvent_TxSuccess" name="Payment approved">
        <bpmn:incoming>Flow_GW_Approved</bpmn:incoming>
      </bpmn:endEvent>

      <!-- Cancel end event (triggers transaction cancellation + auto-compensation) -->
      <bpmn:endEvent id="EndEvent_TxCancelled" name="Payment declined">
        <bpmn:incoming>Flow_GW_Declined</bpmn:incoming>
        <bpmn:cancelEventDefinition id="CancelEventDef_End"/>
      </bpmn:endEvent>

      <!-- Inner sequence flows -->
      <bpmn:sequenceFlow id="Flow_TxStart_Flight" sourceRef="StartEvent_TxBegin" targetRef="Task_ReserveFlight"/>
      <bpmn:sequenceFlow id="Flow_Flight_Hotel" sourceRef="Task_ReserveFlight" targetRef="Task_ReserveHotel"/>
      <bpmn:sequenceFlow id="Flow_Hotel_Car" sourceRef="Task_ReserveHotel" targetRef="Task_ReserveCar"/>
      <bpmn:sequenceFlow id="Flow_Car_Payment" sourceRef="Task_ReserveCar" targetRef="Task_ChargePayment"/>
      <bpmn:sequenceFlow id="Flow_Payment_GW" sourceRef="Task_ChargePayment" targetRef="Gateway_PaymentApproved"/>
      <bpmn:sequenceFlow id="Flow_GW_Approved" sourceRef="Gateway_PaymentApproved" targetRef="EndEvent_TxSuccess">
        <bpmn:conditionExpression>${paymentApproved}</bpmn:conditionExpression>
      </bpmn:sequenceFlow>
      <bpmn:sequenceFlow id="Flow_GW_Declined" sourceRef="Gateway_PaymentApproved" targetRef="EndEvent_TxCancelled"/>

      <!-- Compensation associations -->
      <bpmn:association id="Assoc_Flight" sourceRef="Boundary_CompFlight" targetRef="Task_CancelFlight" associationDirection="One"/>
      <bpmn:association id="Assoc_Hotel" sourceRef="Boundary_CompHotel" targetRef="Task_CancelHotel" associationDirection="One"/>
      <bpmn:association id="Assoc_Car" sourceRef="Boundary_CompCar" targetRef="Task_CancelCar" associationDirection="One"/>
    </bpmn:transaction>

    <!-- ── Cancel boundary event on the transaction (outside) ── -->
    <bpmn:boundaryEvent id="Boundary_TransactionCancelled"
                        attachedToRef="Transaction_BookTrip"
                        cancelActivity="true">
      <bpmn:outgoing>Flow_Cancel_Notify</bpmn:outgoing>
      <bpmn:cancelEventDefinition id="CancelEventDef_Boundary"/>
    </bpmn:boundaryEvent>

    <!-- ── Happy path: confirmation ── -->
    <bpmn:serviceTask id="Task_SendConfirmation" name="Send confirmation"
                      operaton:delegateExpression="${sendConfirmationDelegate}">
      <bpmn:incoming>Flow_Transaction_Confirm</bpmn:incoming>
      <bpmn:outgoing>Flow_Confirm_End</bpmn:outgoing>
    </bpmn:serviceTask>

    <!-- ── Cancel path: notification ── -->
    <bpmn:serviceTask id="Task_NotifyCancellation" name="Notify cancellation"
                      operaton:delegateExpression="${notifyCancellationDelegate}">
      <bpmn:incoming>Flow_Cancel_Notify</bpmn:incoming>
      <bpmn:outgoing>Flow_Notify_End</bpmn:outgoing>
    </bpmn:serviceTask>

    <!-- ── End events ── -->
    <bpmn:endEvent id="EndEvent_TripBooked" name="Trip booked">
      <bpmn:incoming>Flow_Confirm_End</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:endEvent id="EndEvent_TripCancelled" name="Trip cancelled">
      <bpmn:incoming>Flow_Notify_End</bpmn:incoming>
    </bpmn:endEvent>

    <!-- ── Outer sequence flows ── -->
    <bpmn:sequenceFlow id="Flow_Start_Transaction" sourceRef="StartEvent_TripRequested" targetRef="Transaction_BookTrip"/>
    <bpmn:sequenceFlow id="Flow_Transaction_Confirm" sourceRef="Transaction_BookTrip" targetRef="Task_SendConfirmation"/>
    <bpmn:sequenceFlow id="Flow_Cancel_Notify" sourceRef="Boundary_TransactionCancelled" targetRef="Task_NotifyCancellation"/>
    <bpmn:sequenceFlow id="Flow_Confirm_End" sourceRef="Task_SendConfirmation" targetRef="EndEvent_TripBooked"/>
    <bpmn:sequenceFlow id="Flow_Notify_End" sourceRef="Task_NotifyCancellation" targetRef="EndEvent_TripCancelled"/>

  </bpmn:process>

  <!-- ── BPMN DI ── -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="travel-booking">

      <!-- Outer start -->
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_TripRequested">
        <dc:Bounds x="152" y="300" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="131" y="343" width="78" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Transaction subprocess (expanded) -->
      <bpmndi:BPMNShape id="Shape_Transaction" bpmnElement="Transaction_BookTrip" isExpanded="true">
        <dc:Bounds x="250" y="140" width="1000" height="420"/>
        <bpmndi:BPMNLabel><dc:Bounds x="690" y="152" width="120" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Inner start event -->
      <bpmndi:BPMNShape id="Shape_TxStart" bpmnElement="StartEvent_TxBegin">
        <dc:Bounds x="292" y="300" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="271" y="343" width="79" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Reserve flight -->
      <bpmndi:BPMNShape id="Shape_ReserveFlight" bpmnElement="Task_ReserveFlight">
        <dc:Bounds x="390" y="278" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_CompBoundaryFlight" bpmnElement="Boundary_CompFlight">
        <dc:Bounds x="422" y="340" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_CancelFlight" bpmnElement="Task_CancelFlight">
        <dc:Bounds x="390" y="430" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- Reserve hotel -->
      <bpmndi:BPMNShape id="Shape_ReserveHotel" bpmnElement="Task_ReserveHotel">
        <dc:Bounds x="550" y="278" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_CompBoundaryHotel" bpmnElement="Boundary_CompHotel">
        <dc:Bounds x="582" y="340" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_CancelHotel" bpmnElement="Task_CancelHotel">
        <dc:Bounds x="550" y="430" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- Reserve car -->
      <bpmndi:BPMNShape id="Shape_ReserveCar" bpmnElement="Task_ReserveCar">
        <dc:Bounds x="710" y="278" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_CompBoundaryCar" bpmnElement="Boundary_CompCar">
        <dc:Bounds x="742" y="340" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_CancelCar" bpmnElement="Task_CancelCar">
        <dc:Bounds x="710" y="430" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- Charge payment -->
      <bpmndi:BPMNShape id="Shape_ChargePayment" bpmnElement="Task_ChargePayment">
        <dc:Bounds x="870" y="278" width="100" height="80"/>
      </bpmndi:BPMNShape>

      <!-- Payment gateway -->
      <bpmndi:BPMNShape id="Shape_GWPayment" bpmnElement="Gateway_PaymentApproved" isMarkerVisible="true">
        <dc:Bounds x="1030" y="295" width="50" height="50"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1010" y="265" width="90" height="27"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Inner end events -->
      <bpmndi:BPMNShape id="Shape_EndTxSuccess" bpmnElement="EndEvent_TxSuccess">
        <dc:Bounds x="1152" y="302" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1126" y="345" width="88" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_EndTxCancelled" bpmnElement="EndEvent_TxCancelled">
        <dc:Bounds x="1152" y="448" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1130" y="491" width="80" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Cancel boundary on transaction (outer) -->
      <bpmndi:BPMNShape id="Shape_BoundaryTxCancelled" bpmnElement="Boundary_TransactionCancelled">
        <dc:Bounds x="712" y="542" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="744" y="556" width="90" height="27"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Post-transaction: confirmation path -->
      <bpmndi:BPMNShape id="Shape_SendConfirmation" bpmnElement="Task_SendConfirmation">
        <dc:Bounds x="1320" y="278" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_EndTripBooked" bpmnElement="EndEvent_TripBooked">
        <dc:Bounds x="1482" y="300" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1460" y="343" width="80" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Post-transaction: cancellation path -->
      <bpmndi:BPMNShape id="Shape_NotifyCancellation" bpmnElement="Task_NotifyCancellation">
        <dc:Bounds x="1320" y="500" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_EndTripCancelled" bpmnElement="EndEvent_TripCancelled">
        <dc:Bounds x="1482" y="522" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1457" y="565" width="86" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- Sequence flow edges (outer) -->
      <bpmndi:BPMNEdge id="Edge_Start_Transaction" bpmnElement="Flow_Start_Transaction">
        <di:waypoint x="188" y="318"/>
        <di:waypoint x="250" y="318"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Transaction_Confirm" bpmnElement="Flow_Transaction_Confirm">
        <di:waypoint x="1250" y="318"/>
        <di:waypoint x="1320" y="318"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Cancel_Notify" bpmnElement="Flow_Cancel_Notify">
        <di:waypoint x="748" y="560"/>
        <di:waypoint x="1370" y="560"/>
        <di:waypoint x="1370" y="580"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Confirm_End" bpmnElement="Flow_Confirm_End">
        <di:waypoint x="1420" y="318"/>
        <di:waypoint x="1482" y="318"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Notify_End" bpmnElement="Flow_Notify_End">
        <di:waypoint x="1420" y="540"/>
        <di:waypoint x="1482" y="540"/>
      </bpmndi:BPMNEdge>

      <!-- Sequence flow edges (inner, inside transaction) -->
      <bpmndi:BPMNEdge id="Edge_TxStart_Flight" bpmnElement="Flow_TxStart_Flight">
        <di:waypoint x="328" y="318"/>
        <di:waypoint x="390" y="318"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Flight_Hotel" bpmnElement="Flow_Flight_Hotel">
        <di:waypoint x="490" y="318"/>
        <di:waypoint x="550" y="318"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Hotel_Car" bpmnElement="Flow_Hotel_Car">
        <di:waypoint x="650" y="318"/>
        <di:waypoint x="710" y="318"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Car_Payment" bpmnElement="Flow_Car_Payment">
        <di:waypoint x="810" y="318"/>
        <di:waypoint x="870" y="318"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Payment_GW" bpmnElement="Flow_Payment_GW">
        <di:waypoint x="970" y="318"/>
        <di:waypoint x="1030" y="320"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_GW_Approved" bpmnElement="Flow_GW_Approved">
        <di:waypoint x="1080" y="320"/>
        <di:waypoint x="1152" y="320"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1090" y="302" width="50" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_GW_Declined" bpmnElement="Flow_GW_Declined">
        <di:waypoint x="1055" y="345"/>
        <di:waypoint x="1055" y="466"/>
        <di:waypoint x="1152" y="466"/>
        <bpmndi:BPMNLabel><dc:Bounds x="1060" y="398" width="50" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>

      <!-- Compensation associations (no edge waypoints needed — just define the association edge) -->
      <bpmndi:BPMNEdge id="Edge_Assoc_Flight" bpmnElement="Assoc_Flight">
        <di:waypoint x="440" y="376"/>
        <di:waypoint x="440" y="430"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Assoc_Hotel" bpmnElement="Assoc_Hotel">
        <di:waypoint x="600" y="376"/>
        <di:waypoint x="600" y="430"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Assoc_Car" bpmnElement="Assoc_Car">
        <di:waypoint x="760" y="376"/>
        <di:waypoint x="760" y="430"/>
      </bpmndi:BPMNEdge>

    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

- [ ] **Step 2: Create `ReserveFlightDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("reserveFlightDelegate")
public class ReserveFlightDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReserveFlightDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        String flightRef = "FL-" + tripId;
        execution.setVariable("flightRef", flightRef);
        log.info("Flight reserved: {} for trip {}", flightRef, tripId);
    }
}
```

- [ ] **Step 3: Create `ReserveHotelDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("reserveHotelDelegate")
public class ReserveHotelDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReserveHotelDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        String hotelRef = "HT-" + tripId;
        execution.setVariable("hotelRef", hotelRef);
        log.info("Hotel reserved: {} for trip {}", hotelRef, tripId);
    }
}
```

- [ ] **Step 4: Create `ReserveCarDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("reserveCarDelegate")
public class ReserveCarDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReserveCarDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        String carRef = "CR-" + tripId;
        execution.setVariable("carRef", carRef);
        log.info("Car reserved: {} for trip {}", carRef, tripId);
    }
}
```

- [ ] **Step 5: Create `ChargePaymentDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("chargePaymentDelegate")
public class ChargePaymentDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ChargePaymentDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Double flightPrice = (Double) execution.getVariable("flightPrice");
        Double hotelPrice  = (Double) execution.getVariable("hotelPrice");
        Double carPrice    = (Double) execution.getVariable("carPrice");
        Double budget      = (Double) execution.getVariable("budget");

        double total = (flightPrice != null ? flightPrice : 0.0)
                     + (hotelPrice  != null ? hotelPrice  : 0.0)
                     + (carPrice    != null ? carPrice    : 0.0);
        boolean approved = budget != null && total <= budget;

        execution.setVariable("paymentApproved", approved);
        log.info("Payment charge: total={}, budget={}, approved={}", total, budget, approved);
    }
}
```

- [ ] **Step 6: Create `CancelFlightDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("cancelFlightDelegate")
public class CancelFlightDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CancelFlightDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String flightRef = (String) execution.getVariable("flightRef");
        execution.setVariable("flightCancelled", true);
        log.info("Flight cancelled: {}", flightRef);
    }
}
```

- [ ] **Step 7: Create `CancelHotelDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("cancelHotelDelegate")
public class CancelHotelDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CancelHotelDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String hotelRef = (String) execution.getVariable("hotelRef");
        execution.setVariable("hotelCancelled", true);
        log.info("Hotel cancelled: {}", hotelRef);
    }
}
```

- [ ] **Step 8: Create `CancelCarDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("cancelCarDelegate")
public class CancelCarDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(CancelCarDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String carRef = (String) execution.getVariable("carRef");
        execution.setVariable("carCancelled", true);
        log.info("Car cancelled: {}", carRef);
    }
}
```

- [ ] **Step 9: Create `SendConfirmationDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sendConfirmationDelegate")
public class SendConfirmationDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(SendConfirmationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId    = (String) execution.getVariable("tripId");
        String flightRef = (String) execution.getVariable("flightRef");
        String hotelRef  = (String) execution.getVariable("hotelRef");
        String carRef    = (String) execution.getVariable("carRef");
        log.info("Trip {} confirmed — flight={}, hotel={}, car={}", tripId, flightRef, hotelRef, carRef);
    }
}
```

- [ ] **Step 10: Create `NotifyCancellationDelegate.java`**

```java
package org.operaton.examples.travelbooking;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("notifyCancellationDelegate")
public class NotifyCancellationDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(NotifyCancellationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String tripId = (String) execution.getVariable("tripId");
        log.info("Trip {} cancelled — all reservations have been released", tripId);
    }
}
```

- [ ] **Step 11: Verify smoke test still passes**

```bash
cd examples/use-cases/travel-booking
./mvnw verify
```

Expected: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 12: Commit**

```bash
git add examples/use-cases/travel-booking/src
git commit -m "feat(travel-booking): full BPMN transaction model + 9 delegate beans"
```

---

### Task 3: End-to-end integration tests

**Files:**
- Replace: `examples/use-cases/travel-booking/src/test/java/org/operaton/examples/travelbooking/TravelBookingIT.java`

**Interfaces:**
- Consumes: BPMN from Task 2; end event IDs: `EndEvent_TripBooked`, `EndEvent_TripCancelled`
- Produces: 3 passing ITs covering smoke + happy path + cancel/compensation path

**Context on sync vs async:** The process uses only synchronous service tasks inside the transaction, so `startProcessInstanceByKey` will block until the process completes. Assertions can be made immediately after the call returns. We still use Awaitility as a safety net (`atMost(30s)`) because compensation triggered by transaction cancellation may involve an async job in some Operaton configurations. If all tests pass without Awaitility, that confirms synchronous execution; if they fail without it, Awaitility is essential.

- [ ] **Step 1: Replace `TravelBookingIT.java` with full test suite**

```java
package org.operaton.examples.travelbooking;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class TravelBookingIT {

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
            .processDefinitionKey("travel-booking")
            .count()).isEqualTo(1);
    }

    @Test
    void happyPath_withinBudget_booksTrip() {
        // budget 2000, total 1200 (800+300+100) — within budget
        var pi = runtimeService.startProcessInstanceByKey(
            "travel-booking",
            "TRIP-001",
            Variables.createVariables()
                .putValue("tripId", "TRIP-001")
                .putValue("customer", "Alice")
                .putValue("destination", "Paris")
                .putValue("budget", 2000.0)
                .putValue("flightPrice", 800.0)
                .putValue("hotelPrice", 300.0)
                .putValue("carPrice", 100.0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_TripBooked");
        });

        var vars = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId())
            .list();

        assertThat(vars).extracting("name").contains("flightRef", "hotelRef", "carRef");

        var paymentApproved = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("paymentApproved").singleResult();
        assertThat((Boolean) paymentApproved.getValue()).isTrue();

        // Compensation handlers should NOT have run — no *Cancelled variables
        var flightCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("flightCancelled").singleResult();
        assertThat(flightCancelled).isNull();
    }

    @Test
    void cancelPath_overBudget_cancelsAndCompensates() {
        // budget 1000, total 1200 (800+300+100) — over budget
        var pi = runtimeService.startProcessInstanceByKey(
            "travel-booking",
            "TRIP-002",
            Variables.createVariables()
                .putValue("tripId", "TRIP-002")
                .putValue("customer", "Bob")
                .putValue("destination", "Tokyo")
                .putValue("budget", 1000.0)
                .putValue("flightPrice", 800.0)
                .putValue("hotelPrice", 300.0)
                .putValue("carPrice", 100.0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(pi.getId())
                .singleResult();
            assertThat(historic.getState()).isEqualTo(HistoricProcessInstance.STATE_COMPLETED);
            assertThat(historic.getEndActivityId()).isEqualTo("EndEvent_TripCancelled");
        });

        // paymentApproved == false
        var paymentApproved = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("paymentApproved").singleResult();
        assertThat((Boolean) paymentApproved.getValue()).isFalse();

        // All three compensation handlers must have run
        var flightCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("flightCancelled").singleResult();
        assertThat(flightCancelled).isNotNull();
        assertThat((Boolean) flightCancelled.getValue()).isTrue();

        var hotelCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("hotelCancelled").singleResult();
        assertThat(hotelCancelled).isNotNull();
        assertThat((Boolean) hotelCancelled.getValue()).isTrue();

        var carCancelled = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(pi.getId()).variableName("carCancelled").singleResult();
        assertThat(carCancelled).isNotNull();
        assertThat((Boolean) carCancelled.getValue()).isTrue();
    }
}
```

- [ ] **Step 2: Run the full test suite**

```bash
cd examples/use-cases/travel-booking
./mvnw verify
```

Expected: `Tests run: 3, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

If the cancel path test fails because compensation variables are not set, it means the transaction cancel/compensation is asynchronous. In that case, add an Awaitility assertion waiting for `carCancelled` to appear before asserting the others — the await in the test already handles the process end, but the history variables may lag slightly.

- [ ] **Step 3: Also verify with Gradle**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add examples/use-cases/travel-booking/src/test
git commit -m "test(travel-booking): e2e IT — happy path, cancel + compensation path"
```

---

### Task 4: Documentation, BPMN PNG, registry, root README

**Files:**
- Create: `examples/use-cases/travel-booking/README.md`
- Create: `examples/use-cases/travel-booking/src/main/resources/travel-booking.png` (rendered)
- Modify: `.operaton-starter.yml` — add `travel-booking` entry after `insurance-claim`
- Modify: `README.md` (root) — update BPMN Concept Reference table

**Interfaces:**
- Consumes: BPMN from Task 2, ITs from Task 3
- Produces: complete documentation, PNG, registry entry

- [ ] **Step 1: Render BPMN PNG**

```bash
cd examples/use-cases/travel-booking
node --version   # must be ≥ 20; if not: nvm use 20
npx bpmn-to-image src/main/resources/travel-booking.bpmn:src/main/resources/travel-booking.png
```

Expected: `src/main/resources/travel-booking.png` created.

- [ ] **Step 2: Create `README.md`**

```markdown
# Travel Booking SAGA

Demonstrates the BPMN **transaction subprocess** with a **cancel end event** and **cancel boundary event** — the canonical SAGA pattern for all-or-nothing distributed reservations.

## What you will learn

- How a `<bpmn:transaction>` subprocess models an all-or-nothing unit of work
- How a **cancel end event** inside the transaction triggers cancellation and automatically fires compensation for every completed activity
- How a **cancel boundary event** on the outer transaction element routes the cancelled outcome to a separate flow
- How **compensation boundary events** are wired to compensation handler tasks (`isForCompensation="true"`) via associations
- The difference from manual compensation (see `error-compensation`): here, compensation is triggered *implicitly* by transaction cancellation — not thrown by hand

## Process model

![Travel booking SAGA process](src/main/resources/travel-booking.png)

The "Book trip" transaction subprocess reserves flight → hotel → car sequentially, then charges payment. If the total exceeds the budget, the cancel end event fires, rolling back all reservations automatically. If payment succeeds, the transaction completes normally and a confirmation is sent.

## Prerequisites

- JDK 21
- Docker (for local run and integration tests)

## Run it

```bash
docker compose up -d
./mvnw spring-boot:run   # or: ./gradlew bootRun
```

Cockpit: http://localhost:8080 — login `demo` / `demo`

## Walk through it

**Happy path — within budget:**

```bash
curl -X POST http://localhost:8080/engine-rest/process-definition/key/travel-booking/start \
  -H "Content-Type: application/json" \
  -d '{
    "businessKey": "TRIP-HAPPY",
    "variables": {
      "tripId":      { "value": "TRIP-HAPPY", "type": "String" },
      "customer":    { "value": "Alice",      "type": "String" },
      "destination": { "value": "Paris",      "type": "String" },
      "budget":      { "value": 2000,         "type": "Double" },
      "flightPrice": { "value": 800,          "type": "Double" },
      "hotelPrice":  { "value": 300,          "type": "Double" },
      "carPrice":    { "value": 100,          "type": "Double" }
    }
  }'
```

Open Cockpit → History → completed instances. The instance ends at **"Trip booked"**. Variables `flightRef`, `hotelRef`, `carRef` are set; `paymentApproved = true`.

**Cancel path — over budget:**

```bash
curl -X POST http://localhost:8080/engine-rest/process-definition/key/travel-booking/start \
  -H "Content-Type: application/json" \
  -d '{
    "businessKey": "TRIP-CANCEL",
    "variables": {
      "tripId":      { "value": "TRIP-CANCEL", "type": "String" },
      "customer":    { "value": "Bob",         "type": "String" },
      "destination": { "value": "Tokyo",       "type": "String" },
      "budget":      { "value": 1000,          "type": "Double" },
      "flightPrice": { "value": 800,           "type": "Double" },
      "hotelPrice":  { "value": 300,           "type": "Double" },
      "carPrice":    { "value": 100,           "type": "Double" }
    }
  }'
```

The instance ends at **"Trip cancelled"**. Variables `flightCancelled`, `hotelCancelled`, `carCancelled` are all `true` — the compensation handlers ran automatically. `paymentApproved = false`.

## How it works

The BPMN transaction subprocess ([`travel-booking.bpmn`](src/main/resources/travel-booking.bpmn)) wraps all booking steps. Each reservation task has a compensation boundary event wired (via association) to its compensation handler:

| Reservation task | Compensation handler |
|---|---|
| `ReserveFlightDelegate` | `CancelFlightDelegate` — sets `flightCancelled = true` |
| `ReserveHotelDelegate` | `CancelHotelDelegate` — sets `hotelCancelled = true` |
| `ReserveCarDelegate` | `CancelCarDelegate` — sets `carCancelled = true` |

`ChargePaymentDelegate` computes `total = flightPrice + hotelPrice + carPrice` and sets `paymentApproved = total <= budget`. The XOR gateway routes to either the normal end event or the **cancel end event**.

When the cancel end event fires, the BPMN engine automatically compensates every completed task in the transaction (in reverse completion order), then continues from the cancel boundary event on the outer transaction to `NotifyCancellationDelegate` → "Trip cancelled" end event.

## Run the tests

```bash
./mvnw verify      # runs 3 ITs via maven-failsafe-plugin
./gradlew build    # same ITs via JUnit Platform
```

The ITs start a PostgreSQL container via Testcontainers, deploy the BPMN, run the happy path and the cancel/compensation path, and assert end states and variable values.
```

- [ ] **Step 3: Add `travel-booking` entry to `.operaton-starter.yml`**

After the `insurance-claim` block (ends around line 1420), append:

```yaml
  - id: travel-booking
    title: "Use Case — Travel Booking SAGA"
    icon: "✈️"
    path: examples/use-cases/travel-booking
    shortDescription: >
      BPMN transaction subprocess with cancel end event and cancel boundary event —
      all-or-nothing flight, hotel, and car booking with automatic compensation rollback.
    longDescription: |
      A customer books a trip as a BPMN transaction subprocess: flight → hotel → car are
      reserved sequentially, then payment is charged. If the total exceeds the customer's
      budget, a cancel end event fires — the engine automatically compensates every
      completed reservation (flight, hotel, car all cancelled) and routes to a "Trip
      cancelled" outcome via the cancel boundary event.

      - **Transaction subprocess** — first coverage in the catalog; models an all-or-nothing SAGA
      - **Cancel end event** — fires when payment is declined; implicitly triggers transaction cancellation
      - **Cancel boundary event** — attached to the outer transaction; routes the cancelled outcome
      - **Compensation boundary events** — auto-fired by the cancel; no manual throw needed
      - Distinct from `error-compensation`: that example uses a flat process with a manual
        intermediate compensation throw; this example uses the BPMN transaction construct itself
    buildSystem: maven
    runtime: spring-boot
    operatonVersion: "2.1.1"
    javaVersion: "21"
    complexity: intermediate
    tags:
      - { label: "SAGA", category: concept }
      - { label: "Transaction", category: concept }
      - { label: "Cancel Event", category: concept }
      - { label: "Compensation", category: concept }
    integrations: [postgres]
    bpmnConcepts: [transaction-subprocess, cancel-end-event, cancel-boundary-event, compensation-boundary-event, exclusive-gateway]
    requires: "Java 21+, Docker"
    authors:
      - { name: "Karsten Thoms", url: "https://github.com/kthoms" }
    license: "Apache-2.0"
    screenshots:
      - examples/use-cases/travel-booking/src/main/resources/travel-booking.png
    documentationUrl: "https://github.com/kthoms/operaton-examples/blob/main/examples/use-cases/travel-booking/README.md"
    lastUpdated: "2026-06-22"
```

- [ ] **Step 4: Update root `README.md` BPMN Concept Reference table**

In the `### BPMN Concepts` table (around line 125), add after the `Compensation` row:

```markdown
| **Transaction subprocess** | **travel-booking** | All-or-nothing SAGA; cancel end event triggers auto-compensation |
| **Cancel event (end + boundary)** | **travel-booking** | Cancel end event inside transaction + cancel boundary on transaction |
```

Update the existing `Compensation` row to reflect both examples:

```markdown
| Compensation | error-compensation, travel-booking | Manual throw (error-compensation) vs. transaction-driven (travel-booking) |
```

- [ ] **Step 5: Run full test suite one final time**

```bash
cd examples/use-cases/travel-booking
./mvnw verify
```

Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 6: Commit everything**

```bash
git add examples/use-cases/travel-booking/README.md \
        examples/use-cases/travel-booking/src/main/resources/travel-booking.png \
        .operaton-starter.yml \
        README.md
git commit -m "docs(travel-booking): README, BPMN PNG, registry entry, root concept table"
```

---

## Self-Review

**Spec coverage:**
- ✅ Transaction subprocess — Task 2 BPMN
- ✅ Cancel end event — Task 2 BPMN `EndEvent_TxCancelled`
- ✅ Cancel boundary event — Task 2 BPMN `Boundary_TransactionCancelled`
- ✅ Compensation boundary events + handlers — Task 2 BPMN + 3 cancel delegates
- ✅ 9 delegates (all non-stub) — Task 2
- ✅ 3 ITs (smoke + happy + cancel) — Task 3
- ✅ Awaitility, no Thread.sleep — Task 3
- ✅ Testcontainers PostgreSQL, @ServiceConnection — Task 3
- ✅ Root pom.xml + settings.gradle.kts registration — Task 1
- ✅ `.operaton-starter.yml` entry — Task 4
- ✅ Root README concept table update — Task 4
- ✅ README all 8 sections — Task 4
- ✅ BPMN PNG rendered, referenced — Task 4
- ✅ No seeded users (documented exception: no user tasks) — application.yaml

**Placeholder scan:** No TBDs, no TODOs, no stubs. All code complete.

**Type consistency:** `tripId` (String), prices (Double), refs (String), cancelled flags (Boolean) — consistent across all delegates and tests.
