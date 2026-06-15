rootProject.name = "operaton-examples-aggregate"

include(
  "examples:01-getting-started",
  "examples:02-service-tasks",
  "examples:03-external-task-worker",
  "examples:04-user-task-forms",
  "examples:05-dmn-decision",
  "examples:06-message-events",
  "examples:07-timer-events",
  "examples:08-error-compensation",
  "examples:09-multi-instance",
  "examples:10-integration-rest",
  "examples:11-integration-mail",
  "examples:12-integration-kafka",
  "examples:13-call-activity",
  "examples:14-signal-events",
  "examples:15-event-subprocess",
  "examples:16-inclusive-gateway",
  "examples:17-async-continuation",
  "examples:18-integration-connectors",
  "examples:19-runtime-quarkus",
  "examples:20-distribution-tomcat",
  "examples:21-distribution-wildfly",
  "examples:22-operations-flowset-control",
  "examples:23-engine-plugin",
  "examples:24-job-retry-profile",
  "examples:25-command-interceptor",
  "examples:26-process-migration",
  "examples:27-spin-json",
  "examples:28-multi-tenancy",
  "examples:29-bpmn-model-api-parse",
  "examples:30-bpmn-model-api-generate",
  "examples:31-standalone-dmn",
  "examples:32-unit-testing",
  "use-cases:uc-01-leave-request",
  "use-cases:uc-02-loan-application",
  "use-cases:uc-03-incident-management",
  "use-cases:uc-04-order-fulfillment"
)

project(":examples:01-getting-started").projectDir = file("examples/01-getting-started")
project(":examples:02-service-tasks").projectDir = file("examples/02-service-tasks")
project(":examples:03-external-task-worker").projectDir = file("examples/03-external-task-worker")
project(":examples:04-user-task-forms").projectDir = file("examples/04-user-task-forms")
project(":examples:05-dmn-decision").projectDir = file("examples/05-dmn-decision")
project(":examples:06-message-events").projectDir = file("examples/06-message-events")
project(":examples:07-timer-events").projectDir = file("examples/07-timer-events")
project(":examples:08-error-compensation").projectDir = file("examples/08-error-compensation")
project(":examples:09-multi-instance").projectDir = file("examples/09-multi-instance")
project(":examples:10-integration-rest").projectDir = file("examples/10-integration-rest")
project(":examples:11-integration-mail").projectDir = file("examples/11-integration-mail")
project(":examples:12-integration-kafka").projectDir = file("examples/12-integration-kafka")
project(":examples:13-call-activity").projectDir = file("examples/13-call-activity")
project(":examples:14-signal-events").projectDir = file("examples/14-signal-events")
project(":examples:15-event-subprocess").projectDir = file("examples/15-event-subprocess")
project(":examples:16-inclusive-gateway").projectDir = file("examples/16-inclusive-gateway")
project(":examples:17-async-continuation").projectDir = file("examples/17-async-continuation")
project(":examples:18-integration-connectors").projectDir = file("examples/18-integration-connectors")
project(":examples:19-runtime-quarkus").projectDir = file("examples/19-runtime-quarkus")
project(":examples:20-distribution-tomcat").projectDir = file("examples/20-distribution-tomcat")
project(":examples:21-distribution-wildfly").projectDir = file("examples/21-distribution-wildfly")
project(":examples:22-operations-flowset-control").projectDir = file("examples/22-operations-flowset-control")
project(":use-cases:uc-01-leave-request").projectDir = file("use-cases/uc-01-leave-request")
project(":use-cases:uc-02-loan-application").projectDir = file("use-cases/uc-02-loan-application")
project(":use-cases:uc-03-incident-management").projectDir = file("use-cases/uc-03-incident-management")
project(":use-cases:uc-04-order-fulfillment").projectDir = file("use-cases/uc-04-order-fulfillment")
