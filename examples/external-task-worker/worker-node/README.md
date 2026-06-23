# External Task Worker — Node.js variant

A Node.js implementation of the same Operaton **external task pattern** as the
[parent Java example](../README.md): a worker decouples from the engine by polling tasks over
HTTP, locking them, and completing (or failing) them in its own process — here using
[`camunda-external-task-client-js`](https://github.com/camunda/camunda-external-task-client-js).

It drives the **same BPMN model and the same topics** as the Java worker
([`../src/main/resources/order-fulfillment.bpmn`](../src/main/resources/order-fulfillment.bpmn)),
so the two are directly comparable. The only difference is the runtime: a standalone
`operaton/operaton` engine container takes the place of the embedded Spring Boot engine, because
a Node worker cannot host the engine in-process.

## What you will learn

- Build an Operaton external task worker in Node.js with `camunda-external-task-client-js`
- Map the Java handler API to its JS equivalents: `complete`, `handleBpmnError`, `handleFailure`
- Point a worker at a standalone `operaton/operaton` distribution over `/engine-rest`
- Auto-deploy a BPMN into the distribution image via the `configuration/resources` directory
- Test a JS worker end-to-end with Vitest + Testcontainers (PostgreSQL + the real engine image)

## Process model

The model is owned by the parent example — see
[its Process model section](../README.md#process-model). Two external service tasks
(`inventory-check`, `arrange-shipping`) plus an `OUT_OF_STOCK` error boundary event.

## Prerequisites

- Node.js 20+
- Docker (for the engine, PostgreSQL, and the integration tests)

## Run it

```bash
docker compose up -d --wait      # PostgreSQL + operaton/operaton:2.1.1, auto-deploys the BPMN
npm install
npm start                        # node worker.js — polls http://localhost:8080/engine-rest
```

Open http://localhost:8080/operaton — Cockpit and Tasklist, login `demo` / `demo`.
Set `OPERATON_BASE_URL` to point the worker at a different engine.

## Walk through it

### Happy path — order fulfilled

```bash
curl -H 'Content-Type: application/json' \
  -d '{"variables":{"orderId":{"value":"ORD-001","type":"String"},"sku":{"value":"WIDGET-42","type":"String"},"quantity":{"value":2,"type":"Integer"}}}' \
  http://localhost:8080/engine-rest/process-definition/key/order-fulfillment/start
```

The worker completes both tasks; the instance ends at *Order fulfilled* with `reservationId`
and `trackingId` variables.

### Out-of-stock — boundary event path

```bash
curl -H 'Content-Type: application/json' \
  -d '{"variables":{"orderId":{"value":"ORD-002","type":"String"},"sku":{"value":"RARE-ITEM","type":"String"},"quantity":{"value":1,"type":"Integer"},"simulateOutOfStock":{"value":true,"type":"Boolean"}}}' \
  http://localhost:8080/engine-rest/process-definition/key/order-fulfillment/start
```

The worker calls `handleBpmnError("OUT_OF_STOCK")`; the boundary event catches it and the
instance ends at *Order backordered*.

### Transient failure — retry then succeed

```bash
curl -H 'Content-Type: application/json' \
  -d '{"variables":{"orderId":{"value":"ORD-003","type":"String"},"sku":{"value":"WIDGET-1","type":"String"},"quantity":{"value":1,"type":"Integer"},"simulateOneFailure":{"value":true,"type":"Boolean"}}}' \
  http://localhost:8080/engine-rest/process-definition/key/order-fulfillment/start
```

The first lock fails via `handleFailure` with `retries: 2, retryTimeout: 0`; the engine re-queues
the task immediately and the second attempt completes.

Stop the stack with `docker compose down -v` when finished.

## How it works

- [worker.js](worker.js) creates a `Client` and subscribes to `inventory-check` and
  `arrange-shipping`. The handlers mirror the Java
  [`InventoryCheckHandler`](../src/main/java/org/operaton/examples/externaltaskworker/InventoryCheckHandler.java)
  and [`ShippingHandler`](../src/main/java/org/operaton/examples/externaltaskworker/ShippingHandler.java):

  | Concern | Java (`ExternalTaskService`) | Node (`taskService`) |
  |---|---|---|
  | Complete | `complete(task, Map.of(...))` | `complete(task, new Variables().set(...))` |
  | Business error | `handleBpmnError(task, "OUT_OF_STOCK", msg)` | `handleBpmnError(task, "OUT_OF_STOCK", msg)` |
  | Transient failure | `handleFailure(task, msg, details, retries, timeout)` | `handleFailure(task, { errorMessage, errorDetails, retries, retryTimeout })` |

- The engine is the `operaton/operaton:2.1.1` distribution image. The
  [docker-compose.yml](docker-compose.yml) mounts the parent example's `.bpmn` into
  `/operaton/configuration/resources`, which the distribution scans and deploys on startup.
- The worker is a pure client: it never deploys the model and holds no engine state — exactly the
  decoupling the external task pattern exists to provide.

## Run the tests

```bash
npm install
npm test
```

[worker.test.js](worker.test.js) starts PostgreSQL and the `operaton/operaton` image via
Testcontainers, copies the BPMN into the engine's resources directory, runs the worker against the
container, and drives all three paths — fulfilment, out-of-stock boundary event, and
retry-then-succeed — asserting the end event reached for each via the history REST API.
