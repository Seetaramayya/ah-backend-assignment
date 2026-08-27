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

Uses third party service (as defined in the [mock api](#mock-api)) to send invoices to customers.

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
    "invoiceId": "e891827f-487f-4884-a8c3-77316212b81b"
  }
]
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

## To-do and considerations

- **`POST /deliveries` conflates create and update**: the assignment's `POST /deliveries` lets a caller create a
  delivery directly in the terminal `DELIVERED` state, and old clients also rely on calling it twice (once
  `IN_PROGRESS`, then again `DELIVERED`) to finish a delivery. That combined behaviour is kept and made explicit
  (**create-or-complete**), and the endpoint is deprecated (`@Deprecated` on the handler, `Deprecation`/`Link` response
  headers, note in the endpoint table). The clean split is `POST /deliveries/v2` (start, `IN_PROGRESS` only) plus
  `PATCH /deliveries/{id}` (complete, validating the `IN_PROGRESS -> DELIVERED` transition against stored state). Next
  steps: give clients a deprecation window and a `Sunset` date, add read endpoints (`GET /deliveries/{id}`,
  `GET /deliveries`) that the split workflow needs, and once traffic has migrated, remove `POST /deliveries` and rename
  `/deliveries/v2` back to `/deliveries`.
- **Idempotency is natural-key based, not request based**: a repeated `POST /deliveries` or `POST /deliveries/v2` is
  de-duplicated by a `UNIQUE (vehicle_id, started_at)` constraint (`V2__deliveries_natural_key.sql`). On a hit each
  endpoint reconciles by its own rules: `POST /deliveries/v2` (start) only checks the address, since a start request
  carries no state — a retry returns `200` even if the stored delivery has since been completed; `POST /deliveries`
  (create-or-complete) returns `200` unchanged for an identical repeat, applies the `IN_PROGRESS -> DELIVERED`
  transition when the repeat carries it, and returns `409 DELIVERY_CONFLICT` for any other change (different address,
  re-timing a finished delivery, reverting to `IN_PROGRESS`). This assumes `(vehicleId, startedAt)` uniquely identifies
  a delivery. A more robust design is a client-supplied `Idempotency-Key` header backed by an `idempotency_keys` table
  that stores and replays the original response, which also covers retries where the client regenerates the payload.
- **Windows dev script**: `manual-start.sh` (native `./mvnw spring-boot:run` against dockerized dependencies,
  credentials from `.env`) is bash-only. An equivalent `manual-start.cmd`/`manual-start.ps1` for Windows hasn't been
  added yet.
- **CI/CD and image delivery**: `docker-compose up --build` compiles from source on demand, which is fine for local dev
  and for reviewing this assignment, but is not how this should reach production. A real pipeline should build the image
  once in CI, push it to a registry (e.g. ECR), tag it immutably (git SHA/semver), and have production deploy that
  pre-built image rather than building on a production host.
- **Authentication**: JWT validation is wired up (see [Authentication (local/dev)](#authentication-localdev)), but it
  validates against a locally-configured secret with no real identity provider behind it. In production this should
  integrate with AH's actual internal auth/identity platform instead of a project-local secret.

## Sending in the assignment

- We expect a docker compose file that we can run with `docker-compose up` which should start up a functional
  application at port 8080 (including dependencies like a database).
- Create a pull request with your changes. Notify us via e-mail when the assignment is ready for review.

Thank you for your interest and time invested into making this assignment.
