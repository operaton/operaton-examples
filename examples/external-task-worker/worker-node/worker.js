import { Client, Variables, logger } from "camunda-external-task-client-js";

// Topics mirror the external service tasks in ../src/main/resources/order-fulfillment.bpmn
export const INVENTORY_TOPIC = "inventory-check";
export const SHIPPING_TOPIC = "arrange-shipping";

// Tracks which instances have already failed once, for the transient-failure demo
// (mirrors the AtomicBoolean in the Java OrderFulfillmentIT inline worker).
const inventoryFailedOnce = new Set();

export function subscribe(client) {
  client.subscribe(INVENTORY_TOPIC, async ({ task, taskService }) => {
    if (task.variables.get("simulateOutOfStock") === true) {
      // BpmnError routes to the OUT_OF_STOCK boundary event — a business decision, not a retry
      await taskService.handleBpmnError(task, "OUT_OF_STOCK", "Requested item is out of stock");
      return;
    }

    if (task.variables.get("simulateOneFailure") === true && !inventoryFailedOnce.has(task.processInstanceId)) {
      inventoryFailedOnce.add(task.processInstanceId);
      // First attempt fails; retries > 0 with retryTimeout 0 re-queues the task immediately
      await taskService.handleFailure(task, {
        errorMessage: "Transient error",
        errorDetails: "DB timeout",
        retries: 2,
        retryTimeout: 0
      });
      return;
    }

    await taskService.complete(task, new Variables().set("reservationId", "RES-" + task.id));
  });

  client.subscribe(SHIPPING_TOPIC, async ({ task, taskService }) => {
    // A real implementation would call a carrier API here; simplified for the example
    await taskService.complete(task, new Variables().set("trackingId", "TRK-" + task.id));
  });

  return client;
}

export function createWorker(baseUrl) {
  const client = new Client({
    baseUrl,
    use: logger,
    lockDuration: 10000,
    asyncResponseTimeout: 10000
  });
  return subscribe(client);
}

// Run directly: `node worker.js` (set OPERATON_BASE_URL to override the default)
if (import.meta.url === `file://${process.argv[1]}`) {
  const baseUrl = process.env.OPERATON_BASE_URL ?? "http://localhost:8080/engine-rest";
  createWorker(baseUrl);
  console.log(`External task worker polling ${baseUrl}`);
}
