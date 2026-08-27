# Albert Heijn Backend Technical Assignment

## Assignment

The assignment is to implement a microservice using Spring boot. The expected REST endpoints schema is described
below. \
The assignment is rather open-ended and expects you to think of code structure and implementation yourself. \
Follow the requirements below and be ready to support decisions made in implementing this service.

### Endpoints

<table>
<tr>
   <td>Endpoint</td><td>Description</td><td>Request body example</td><td>Response body example</td>
</tr>
<!-- POST /deliveries -->
<tr>
   <td>POST /deliveries</td>
   <td>

**Deprecated** (kept for backward compatibility). This endpoint mixes "start a delivery" and "record an
already-completed delivery" in one call. New clients should use `POST /deliveries/v2` to start a delivery and
`PATCH /deliveries/{id}` to complete it. Responses carry a `Deprecation: true` header and a `Link` to the successor.

Creates a new delivery. <br>`status` is only allowed to be `IN_PROGRESS` or `DELIVERED`. For status `IN_PROGRESS` the
`finishedAt` field must be `null`. For status `DELIVERED` the `finishedAt` field must be provided and must be after
`startedAt` (otherwise `400`).

A delivery is keyed by `(vehicleId, startedAt)`, and this endpoint is **create-or-complete**: the first request
returns `201`; an identical repeat returns `200` with the stored delivery (no duplicate); a repeat that carries the
`IN_PROGRESS → DELIVERED` transition for the same delivery completes it and returns `200` (same effect as
`PATCH /deliveries/{id}`); any other change for the same key (different address, re-timing a finished delivery,
reverting to `IN_PROGRESS`) returns `409`.

   </td>
   <td>

   ```json
   {
  "vehicleId": "AHV-589",
  "address": "Example street 15A",
  "startedAt": "2023-10-09T12:45:34.678Z",
  "status": "IN_PROGRESS"
}
   ```

   </td>
   <td>

   ```json
   {
  "id": "69201507-0ae4-4c56-ac2d-75fbe27efad8",
  "vehicleId": "AHV-589",
  "address": "Example street 15A",
  "startedAt": "2023-10-09T12:45:34.678Z",
  "finishedAt": null,
  "status": "IN_PROGRESS"
}
   ```

   </td>
</tr>

<!-- POST /deliveries/v2 -->
<tr>
   <td>POST /deliveries/v2</td>
   <td>

Starts a new delivery. Always created with status `IN_PROGRESS` and no `finishedAt` (a delivery can only be *started*
here; completing it is `PATCH /deliveries/{id}`). Keyed by `(vehicleId, startedAt)` and safe to retry: first call
returns `201`, a repeat with the same key returns `200` with the existing delivery.

   </td>
   <td>

   ```json
   {
  "vehicleId": "AHV-589",
  "address": "Example street 15A",
  "startedAt": "2023-10-09T12:45:34.678Z"
}
   ```

   </td>
   <td>

   ```json
   {
  "id": "69201507-0ae4-4c56-ac2d-75fbe27efad8",
  "vehicleId": "AHV-589",
  "address": "Example street 15A",
  "startedAt": "2023-10-09T12:45:34.678Z",
  "finishedAt": null,
  "status": "IN_PROGRESS"
}
   ```

   </td>
</tr>

<!-- PATCH /deliveries/{id} -->
<tr>
   <td>PATCH /deliveries/{id}</td>
   <td>

Completes a delivery. Only the `IN_PROGRESS -> DELIVERED` transition is supported: `status` must be `DELIVERED` and
`finishedAt` must be provided and after the stored `startedAt`. Returns `400` if `finishedAt` is not after `startedAt`,
`404` if the delivery does not exist, and `409` if it is not `IN_PROGRESS`.

   </td>
   <td>

   ```json
   {
  "status": "DELIVERED",
  "finishedAt": "2023-10-09T13:30:00.000Z"
}
   ```

   </td>
   <td>

   ```json
   {
  "id": "69201507-0ae4-4c56-ac2d-75fbe27efad8",
  "vehicleId": "AHV-589",
  "address": "Example street 15A",
  "startedAt": "2023-10-09T12:45:34.678Z",
  "finishedAt": "2023-10-09T13:30:00.000Z",
  "status": "DELIVERED"
}
   ```

   </td>
</tr>

<!-- POST /deliveries/invoice -->
<tr>
   <td>POST /deliveries/invoice</td>
   <td>

**Asynchronous — returns `202 Accepted`.** The third party is **not** called on this request: the batch is persisted
and a background `@Scheduled` `InvoicePoller` sends each invoice with retry + circuit breaker. The `202` body lists a
`status` per delivery — resolved now for anything already known, `PENDING` (with `invoiceId: null`) for what was
queued. Clients then poll **`GET /deliveries/{deliveryId}/invoice`** for the final `SUCCEEDED` / `FAILED`.

Details: batch size capped (`app.invoice.max-delivery-ids`, default **100**; over the cap is `400`). Per delivery: one
already `SUCCEEDED` or still `PENDING` is returned as-is and **not** re-queued (a retried POST is safe); an unknown id
returns `FAILED` + `error`; one with no prior item, or whose last attempt `FAILED`, is (re)queued as `PENDING`. Poll
interval is `app.invoice.poll-interval-ms`.

   </td>
   <td>

   ```json
   {
  "deliveryIds": [
    "7167fc04-0625-49fc-98a9-8785a4a32b60"
  ]
}
   ```

   </td>
   <td>

   ```json
   [
  {
    "deliveryId": "7167fc04-0625-49fc-98a9-8785a4a32b60",
    "invoiceId": null,
    "status": "PENDING",
    "error": null
  }
]
   ```

   </td>
</tr>

<!-- GET /deliveries/{deliveryId}/invoice -->
<tr>
   <td>GET /deliveries/{deliveryId}/invoice</td>
   <td>

Latest invoicing outcome for a delivery (for polling a `PENDING` item from `POST /deliveries/invoice`). `404` if the
delivery was never in an invoice batch.

   </td>
   <td colspan="1">&nbsp;</td>
   <td>

   ```json
   {
  "deliveryId": "7167fc04-0625-49fc-98a9-8785a4a32b60",
  "invoiceId": "e891827f-487f-4884-a8c3-77316212b81b",
  "status": "SUCCEEDED",
  "error": null
}
   ```

   </td>
</tr>

<!-- GET /deliveries/business-summary -->
<tr>
   <td>GET /deliveries/business-summary</td>
   <td colspan="2">

Business wants a summary of yesterday's deliveries (Amsterdam time).<br>The summary must include how many deliveries
were **started**. The summary should also include the average time between delivery start. This means if there are 3
deliveries that started at `01:00`, `03:00` and `09:00` the time between starting deliveries is `2 hours` (01:00-03:00)
and `6 hours` (03:00 - 09:00) so the average is `4 hours` or `240 minutes`

   </td>
   <td>

   ```json
   {
  "deliveries": 3,
  "averageMinutesBetweenDeliveryStart": 240
}
   ```

   </td>
</tr>
</table>

## Architecture

All `/deliveries*` calls carry a Bearer JWT. Delivery writes (`POST /deliveries`, `/deliveries/v2`, `PATCH
/deliveries/{id}`) and `GET /deliveries/business-summary` are fully synchronous: controller → service → PostgreSQL.
`POST /deliveries/invoice` is the exception: the service resolves already-handled deliveries, persists the rest as
`PENDING` rows, and returns `202` **without** calling the third party. A `@Scheduled` `InvoicePoller` then drains the
`PENDING` rows, calling the third-party invoice API through `InvoiceClient` (resilience4j `@Retry` with exponential
backoff plus `@CircuitBreaker`) and marking each `SUCCEEDED` or `FAILED`. Clients poll
`GET /deliveries/{deliveryId}/invoice` for the result.

```mermaid
flowchart TB
    client([Client]) -->|Bearer JWT| ctl

    subgraph sync["Synchronous (request thread)"]
        ctl["Controllers<br/>Delivery, Invoice, BusinessSummary"] --> svc["Services"]
    end

    svc -->|"delivery writes + business-summary<br/>invoice: queue PENDING, return 202"| db[("PostgreSQL<br/>Flyway migrations")]

    subgraph async["Asynchronous (@Scheduled)"]
        poller["InvoicePoller<br/>every poll-interval-ms"] --> iclient["InvoiceClient<br/>@Retry (exp. backoff) + @CircuitBreaker"]
    end

    db -->|read PENDING items| poller
    iclient -->|POST /v1/invoices| third["Third-party invoice API<br/>WireMock in dev and test"]
    poller -->|write SUCCEEDED / FAILED| db
```

## Mock API

A mock API is exposed on port `8000` which is defined in the [docker-compose file](./docker-compose.yml#L4), this mock
API must not be modified. The endpoint exposed in this API is used for the `/deliveries/invoice` task. The mock API
exposes the following endpoint.
<!-- POST /v1/invoices -->
<table>
<tr>
   <td>Endpoint</td><td>Request body example</td><td>Response body example</td>
</tr>
<tr>
   <td>POST /v1/invoices</td>
   <td>

   ```json
   {
  "deliveryId": "7167fc04-0625-49fc-98a9-8785a4a32b60",
  "address": "Example street 15A"
}
   ```

   </td>
   <td>

   ```json
   {
  "id": "e891827f-487f-4884-a8c3-77316212b81b",
  "sent": true
}
   ```

   </td>
</tr>
</table>

## Authentication (local/dev)

All `/deliveries*` endpoints require a `Bearer` JWT (validated against a locally-configured secret, see
`app.security.jwt.secret`; no real identity provider is wired up yet). Generate a fresh token (valid 30 days) with
`./mvnw test -Dtest=SampleTokenGeneratorTest` and read it from the console output (no database or running app needed),
or use this pre-generated sample (valid until 2026-09-26T07:08:00Z, this token generated on 2026-08-27T07:08:00Z). This
sample only works if your `.env`'s `JWT_SECRET` is still the default value from `.env.example` — if you've overridden it,
generate your own token instead, since it will be signed with your secret:

```
eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3OTA0MDY0ODAsInN1YiI6ImxvY2FsLWRldi11c2VyIiwiaWF0IjoxNzg3ODE0NDgwfQ.PY2jtpWRtWq4Zdj6CeF5PneBJrpCptn7xGFGg0Od6Rk
```

```
curl -H "Authorization: Bearer <token>" http://localhost:8080/deliveries/business-summary
```

## Requirements

- We do not expect you to spend more than 3 hours on the assignment. You can add items to [**To-do and considerations
  **](#to-do-and-considerations) for anything that you wanted to do but did not have enough time to complete. In the
  follow-up interview this assignment will be discussed and you can elaborate/expand on decisions made in the
  assignment.
- Write the assignment in **Kotlin** if you are proficient with it. It's also possible to write the assignment in **Java
  **. However, we prefer **Kotlin** as it is our primary programming language.
- Use **Git** and commit often, so we can see the iterations made on the code.
- The above REST endpoints are implemented (also following the requirements in the description).
- The data is stored in a `database`, you can choose what type.
- This is a customer facing application, which means a website will use this data to display it to the user.
- Assume this is **production code** that will run in production at Albert Heijn and will be maintained/modified for a
  long time. Not all requirements for "production ready" are listed here, we expect you to decide what is necessary and
  to support your reasoning. Anything you weren't able to implement, think should be implemented, and other
  considerations should be added in the README under [**To-do and considerations**](#to-do-and-considerations).

## Where to start

- An empty application is already set up. You are expected to add the endpoint implementations yourself.
- [A docker-compose file](./docker-compose.yml) already exists that builds and runs the application. Run this to make
  the [mock API](#mock-api) and database (that you add yourself) available. You can use the following command
  `docker-compose up --build`

## Building and testing

- Compiles to **JDK 21** bytecode (`java.version` in `pom.xml`); verified running on JDK 21 through 25. JaCoCo is
  pinned to `0.8.13` so coverage instrumentation works on newer JDKs.
- Build: `./mvnw clean package` — Test: `./mvnw test` (needs Docker for the Testcontainers Postgres) — Single test:
  `./mvnw test -Dtest=ClassName`
- No environment setup is required for tests or a local run: `app.security.jwt.secret` falls back to a dev value in
  `application.yml`, overridden by `APP_SECURITY_JWT_SECRET` (see `.env` / `manual-start.sh`) everywhere else.

### Load test (Gatling)

`src/test/kotlin/.../loadtest/DeliveryLoadSimulation.kt`. Each virtual user is a **driver** (one `vehicleId`) that
creates `-Ddeliveries` deliveries, one every `-Dpace` seconds, each running the full flow: `POST /deliveries/v2` →
`PATCH /deliveries/{id}` → `POST /deliveries/invoice` → `GET /deliveries/{id}/invoice`. `-Dusers` drivers are injected
over `-Dramp` seconds. `GET /deliveries/business-summary` runs alongside as steady read traffic. Runs against a
**running** app, so it is not part of `./mvnw test` or CI.

```
docker-compose up --build          # app on :8080
./generate-token.sh                # writes manual-scripts/.token, read automatically by the sim
./mvnw test-compile gatling:test -Dusers=2000 -Dramp=60 -Ddeliveries=5 -Dpace=12
```

Defaults: `users=20`, `ramp=60`, `deliveries=5`, `pace=12`, `base.url=http://localhost:8080`. Report:
`target/gatling/deliveryloadsimulation-<timestamp>/index.html`; assertions are global p99 < 2 s and failure rate
< 1 %. Kept reports and their analysis live in [`gatling-reports/`](./gatling-reports/README.md).

### Observability (local)

`docker-compose up` also starts **Prometheus** (`:9090`) and **Grafana** (`:3001`, anonymous admin). Prometheus
scrapes the app's `/actuator/prometheus` every 5 s. That endpoint is only `permitAll` under the `docker` profile
(`app.observability.prometheus-scrape-open`); everywhere else it stays authenticated like the rest of `/actuator/*`
(only `health` and `info` are ever public). `micrometer-registry-prometheus` provides the metrics. Grafana auto-loads
a **Backend Assignment** dashboard from `observability/grafana/`: HikariCP pool (active / idle / **pending** / acquire
wait), per-endpoint p95/p99, request rate by status, JVM heap, GC pause — enough to watch a Gatling run and tell
"raise the pool" from "the DB is the limit". To skip it: `docker-compose up application database mock-api`.

## To-do and considerations

- **Rate limiting is not implemented**: nothing throttles callers. `POST /deliveries/invoice` especially needs a
  per-client limit because every accepted batch schedules downstream work. A token-bucket limiter (bucket4j or
  resilience4j `@RateLimiter`) on the write endpoints, or enforcement at the ingress/gateway, should be added.
- **`POST /deliveries` is a backward-compatibility endpoint**: it is deprecated and kept only for old clients
  that create a delivery and later "create" it again as `DELIVERED` to finish it; `POST /deliveries/v2` (start) +
  `PATCH /deliveries/{id}` (complete) are the clean split. Next: a deprecation window with a `Sunset` date,
  `GET /deliveries/{id}` and `GET /deliveries` read endpoints for the split workflow, then drop `POST /deliveries` and
  rename `/deliveries/v2` back.
- **Request idempotency is natural-key based**: repeat `POST /deliveries` / `/v2` are de-duplicated by the
  `UNIQUE (vehicle_id, started_at)` constraint, which does not cover a client that regenerates the payload (e.g. a
  fresh `startedAt`) on retry. A client-supplied `Idempotency-Key` header backed by an `idempotency_keys` table that
  stores and replays the original response is the robust design.
- **Invoice flow — remaining work**: (a) the poller is single-instance safe only — multiple nodes need it to claim rows
  with `SELECT ... FOR UPDATE SKIP LOCKED` (or a lease column); (b) an item that fails after its retries is marked
  `FAILED` and never tried again — an `attempts` column with a threshold would let the poller re-try transient failures
  across ticks; (c) backoff has no jitter; (d) two `POST /deliveries/invoice` calls for the same delivery at the exact
  same instant can both queue it (a *retried* POST is already safe) — a partial unique index on
  `invoice_request_items (delivery_id) WHERE status IN ('PENDING','SUCCEEDED')` closes it; (e) `RestClientConfig` sets
  no connect/read timeouts, so a hung invoice service holds a poller thread until the socket gives up.
- **CI/CD**: `.github/workflows/ci.yml` runs `./mvnw clean verify` (tests + coverage gate) on every push and PR. Still
  missing for delivery: build the image once, push it to a registry, tag it immutably (git SHA / semver), and have
  production deploy that instead of `docker-compose up --build` compiling on the host.
- **Actuator in production**: `/actuator/health` and `/actuator/info` are public; `/actuator/prometheus` is opened
  only under the `docker` profile for the local scrape, authenticated otherwise. In a real deployment actuator would
  move to a dedicated `management.server.port` reachable only by the in-cluster metrics collector (NetworkPolicy),
  not shared with the app's public port.
- **Authentication**: JWT validation is wired up but against a project-local secret with no identity provider behind it
  (see [Authentication (local/dev)](#authentication-localdev)); production should integrate AH's internal auth platform.

## Sending in the assignment

- We expect a docker compose file that we can run with `docker-compose up` which should start up a functional
  application at port 8080 (including dependencies like a database).
- Create a pull request with your changes. Notify us via e-mail when the assignment is ready for review.

Thank you for your interest and time invested into making this assignment.
