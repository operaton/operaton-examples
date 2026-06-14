rootProject.name = "operaton-examples-aggregate"

include(
  "examples:01-getting-started",
  "examples:02-service-tasks",
  "examples:03-external-task-worker",
  "examples:04-user-task-forms",
  "examples:05-dmn-decision",
  "examples:06-message-events",
  "examples:07-timer-events"
)

project(":examples:01-getting-started").projectDir = file("examples/01-getting-started")
project(":examples:02-service-tasks").projectDir = file("examples/02-service-tasks")
project(":examples:03-external-task-worker").projectDir = file("examples/03-external-task-worker")
project(":examples:04-user-task-forms").projectDir = file("examples/04-user-task-forms")
project(":examples:05-dmn-decision").projectDir = file("examples/05-dmn-decision")
project(":examples:06-message-events").projectDir = file("examples/06-message-events")
project(":examples:07-timer-events").projectDir = file("examples/07-timer-events")
