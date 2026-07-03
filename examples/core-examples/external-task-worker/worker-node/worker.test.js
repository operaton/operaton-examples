import { fileURLToPath } from "node:url";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { GenericContainer, Network, Wait } from "testcontainers";
import { createWorker } from "./worker.js";

const BPMN = fileURLToPath(new URL("../src/main/resources/order-fulfillment.bpmn", import.meta.url));
const PROCESS_KEY = "order-fulfillment";

let network;
let postgres;
let engine;
let baseUrl;
let worker;

beforeAll(async () => {
  network = await new Network().start();

  postgres = await new GenericContainer("postgres:16-alpine")
    .withNetwork(network)
    .withNetworkAliases("postgres")
    .withEnvironment({
      POSTGRES_DB: "operaton",
      POSTGRES_USER: "operaton",
      POSTGRES_PASSWORD: "operaton"
    })
    .withWaitStrategy(Wait.forListeningPorts())
    .start();

  engine = await new GenericContainer("operaton/operaton:2.1.1")
    .withNetwork(network)
    .withCommand(["./operaton.sh", "--rest", "--webapps"])
    .withEnvironment({
      DB_DRIVER: "org.postgresql.Driver",
      DB_URL: "jdbc:postgresql://postgres:5432/operaton",
      DB_USERNAME: "operaton",
      DB_PASSWORD: "operaton"
    })
    .withCopyFilesToContainer([
      { source: BPMN, target: "/operaton/configuration/resources/order-fulfillment.bpmn" }
    ])
    .withExposedPorts(8080)
    // Engine is ready only once it has auto-deployed the BPMN from the resources dir
    .withWaitStrategy(
      Wait.forHttp(`/engine-rest/process-definition/key/${PROCESS_KEY}`, 8080).forStatusCode(200)
    )
    .withStartupTimeout(120000)
    .start();

  baseUrl = `http://${engine.getHost()}:${engine.getMappedPort(8080)}/engine-rest`;
  worker = createWorker(baseUrl);
}, 180000);

afterAll(async () => {
  await worker?.stop();
  await engine?.stop();
  await postgres?.stop();
  await network?.stop();
});

async function startInstance(variables) {
  const res = await fetch(`${baseUrl}/process-definition/key/${PROCESS_KEY}/start`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ variables })
  });
  expect(res.status).toBe(200);
  return (await res.json()).id;
}

// The REST history DTO has no endActivityId field, so the end event is read from
// the activity-instance history (noneEndEvent), mirroring the distribution examples.
async function awaitEndActivity(id) {
  for (let attempt = 0; attempt < 60; attempt++) {
    const res = await fetch(
      `${baseUrl}/history/activity-instance?processInstanceId=${id}&activityType=noneEndEvent`
    );
    if (res.status === 200) {
      const [end] = await res.json();
      if (end) {
        return end.activityId;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Process instance ${id} did not reach an end event in time`);
}

async function historicVariable(id, name) {
  const res = await fetch(`${baseUrl}/history/variable-instance?processInstanceId=${id}&variableName=${name}`);
  const [variable] = await res.json();
  return variable?.value;
}

describe("order-fulfillment external task worker (Node.js)", () => {
  it("fulfills an order through complete on both topics", async () => {
    const id = await startInstance({
      orderId: { value: "ORD-001", type: "String" },
      sku: { value: "WIDGET-42", type: "String" },
      quantity: { value: 2, type: "Integer" }
    });

    expect(await awaitEndActivity(id)).toBe("EndEvent_OrderFulfilled");
    expect(await historicVariable(id, "reservationId")).toMatch(/^RES-/);
    expect(await historicVariable(id, "trackingId")).toMatch(/^TRK-/);
  });

  it("routes an out-of-stock order through the BpmnError boundary event", async () => {
    const id = await startInstance({
      orderId: { value: "ORD-002", type: "String" },
      sku: { value: "RARE-ITEM", type: "String" },
      quantity: { value: 1, type: "Integer" },
      simulateOutOfStock: { value: true, type: "Boolean" }
    });

    expect(await awaitEndActivity(id)).toBe("EndEvent_OrderBackordered");
  });

  it("retries a transient failure and eventually completes", async () => {
    const id = await startInstance({
      orderId: { value: "ORD-003", type: "String" },
      sku: { value: "WIDGET-1", type: "String" },
      quantity: { value: 1, type: "Integer" },
      simulateOneFailure: { value: true, type: "Boolean" }
    });

    expect(await awaitEndActivity(id)).toBe("EndEvent_OrderFulfilled");
  });
});
