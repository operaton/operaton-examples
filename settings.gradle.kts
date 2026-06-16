rootProject.name = "operaton-examples-aggregate"

include(
  "examples:getting-started",
  "examples:service-tasks",
  "examples:external-task-worker",
  "examples:user-task-forms",
  "examples:dmn-decision",
  "examples:message-events",
  "examples:timer-events",
  "examples:error-compensation",
  "examples:multi-instance",
  "examples:integration-rest",
  "examples:integration-mail",
  "examples:integration-kafka",
  "examples:call-activity",
  "examples:signal-events",
  "examples:event-subprocess",
  "examples:inclusive-gateway",
  "examples:async-continuation",
  "examples:integration-connectors",
  "examples:runtime-quarkus",
  "examples:distribution-tomcat",
  "examples:distribution-wildfly",
  "examples:engine-plugin",
  "examples:job-retry-profile",
  "examples:command-interceptor",
  "examples:process-migration",
  "examples:spin-json",
  "examples:multi-tenancy",
  "examples:bpmn-model-api-parse",
  "examples:bpmn-model-api-generate",
  "examples:unit-testing",
  "examples:use-cases:leave-request",
  "examples:use-cases:loan-application",
  "examples:use-cases:incident-management",
  "examples:use-cases:order-fulfillment"
)

project(":examples:getting-started").projectDir = file("examples/getting-started")
project(":examples:service-tasks").projectDir = file("examples/service-tasks")
project(":examples:external-task-worker").projectDir = file("examples/external-task-worker")
project(":examples:user-task-forms").projectDir = file("examples/user-task-forms")
project(":examples:dmn-decision").projectDir = file("examples/dmn-decision")
project(":examples:message-events").projectDir = file("examples/message-events")
project(":examples:timer-events").projectDir = file("examples/timer-events")
project(":examples:error-compensation").projectDir = file("examples/error-compensation")
project(":examples:multi-instance").projectDir = file("examples/multi-instance")
project(":examples:integration-rest").projectDir = file("examples/integration-rest")
project(":examples:integration-mail").projectDir = file("examples/integration-mail")
project(":examples:integration-kafka").projectDir = file("examples/integration-kafka")
project(":examples:call-activity").projectDir = file("examples/call-activity")
project(":examples:signal-events").projectDir = file("examples/signal-events")
project(":examples:event-subprocess").projectDir = file("examples/event-subprocess")
project(":examples:inclusive-gateway").projectDir = file("examples/inclusive-gateway")
project(":examples:async-continuation").projectDir = file("examples/async-continuation")
project(":examples:integration-connectors").projectDir = file("examples/integration-connectors")
project(":examples:runtime-quarkus").projectDir = file("examples/runtime-quarkus")
project(":examples:distribution-tomcat").projectDir = file("examples/distribution-tomcat")
project(":examples:distribution-wildfly").projectDir = file("examples/distribution-wildfly")
project(":examples:engine-plugin").projectDir = file("examples/engine-plugin")
project(":examples:job-retry-profile").projectDir = file("examples/job-retry-profile")
project(":examples:command-interceptor").projectDir = file("examples/command-interceptor")
project(":examples:process-migration").projectDir = file("examples/process-migration")
project(":examples:spin-json").projectDir = file("examples/spin-json")
project(":examples:multi-tenancy").projectDir = file("examples/multi-tenancy")
project(":examples:bpmn-model-api-parse").projectDir = file("examples/bpmn-model-api-parse")
project(":examples:bpmn-model-api-generate").projectDir = file("examples/bpmn-model-api-generate")
project(":examples:unit-testing").projectDir = file("examples/unit-testing")
project(":examples:use-cases:leave-request").projectDir = file("examples/use-cases/leave-request")
project(":examples:use-cases:loan-application").projectDir = file("examples/use-cases/loan-application")
project(":examples:use-cases:incident-management").projectDir = file("examples/use-cases/incident-management")
project(":examples:use-cases:order-fulfillment").projectDir = file("examples/use-cases/order-fulfillment")
