# Gatling load-test reports

One HTML report per release, kept out of `target/` (which `mvn clean` wipes). **Retention: keep the
last 5**, delete older ones. Long term this belongs in object storage / a shared drive (S3, GCS,
Confluence, ...) rather than the repo; in-repo is good enough for this assignment.

## How a report is produced

```
docker-compose up --build            # app :8080, mock invoice API :8000
./generate-token.sh                  # writes manual-scripts/.token
./mvnw test-compile gatling:test -Dusers=<N> -Dramp=60 -Ddeliveries=5 -Dpace=12
mv target/gatling/deliveryloadsimulation-<ts> gatling-reports/
```

Simulation: `src/test/kotlin/.../loadtest/DeliveryLoadSimulation.kt`. Each virtual user is a
**driver** (one `vehicleId`) that creates `deliveries` deliveries, one every `pace` seconds, each:
`POST /deliveries/v2` → `PATCH /deliveries/{id}` → `POST /deliveries/invoice` → `pause 500 ms` →
`GET /deliveries/{id}/invoice`. `users` drivers are injected over `ramp` seconds; the run lasts
`ramp + deliveries * pace` seconds. `GET /deliveries/business-summary` runs alongside at
`users / 20` req/s.

Assertions: global p99 < 2000 ms, failure rate < 1 %.

Environment: single local PostgreSQL, WireMock invoice API (responds instantly), `InvoicePoller`
on its 5 s interval throughout. Numbers are directional, not production SLOs.

---

## 5000 drivers × 5 deliveries — HikariCP pool raised to 50

`-Dusers=5000 -Dramp=60 -Ddeliveries=5 -Dpace=12`, ~120 s, after setting
`spring.datasource.hikari.maximum-pool-size: 50` (was the default 10). Same load as the run below;
report kept on disk only (`deliveryloadsimulation-20260827115449647`), not committed.

| Request | count | p50 | p95 | p99 | max | mean | req/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| POST /deliveries/v2 | 25 000 | 1 | 6 | 49 | 272 | 3 | 208 |
| PATCH /deliveries/{id} | 25 000 | 1 | 4 | 35 | 232 | 2 | 208 |
| POST /deliveries/invoice | 25 000 | 1 | 4 | 29 | 241 | 2 | 208 |
| GET /deliveries/{id}/invoice | 25 000 | 1 | 3 | 37 | 228 | 2 | 208 |
| GET /deliveries/business-summary | 30 000 | 1 | 4 | 36 | 297 | 3 | 250 |
| **All** | **130 000** | 1 | 4 | **38** | 297 | 2 | **1083** |

**The pool was the bottleneck.** Same 1083 req/s and 0 failures, but with 5× the connections the
tail collapsed:

| | pool 10 | pool 50 |
|---|---:|---:|
| global p95 | 36 ms | **4 ms** |
| global p99 | 165 ms | **38 ms** |
| `POST /deliveries/v2` p95 | 57 ms | **6 ms** |
| `POST /deliveries/v2` p99 | 207 ms | **49 ms** |
| global max | 773 ms | **297 ms** |

With pool 10, ~400 concurrent requests contended for 10 connections; at 50 the queue mostly
disappears and latency is back near the 2000-driver numbers. Next bottleneck (if pushed harder)
would be Postgres itself — connection count, locks, or the `business-summary` aggregate.

---

## `deliveryloadsimulation-20260827113519986` — 5000 drivers × 5 deliveries (pool 10, default)

`-Dusers=5000 -Dramp=60 -Ddeliveries=5 -Dpace=12`, ~120 s. Both assertions **passed**.

| Request | count | p50 | p95 | p99 | max | mean | req/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| POST /deliveries/v2 | 25 000 | 1 | 57 | **207** | 639 | 11 | 208 |
| PATCH /deliveries/{id} | 25 000 | 1 | 35 | 148 | 675 | 8 | 208 |
| POST /deliveries/invoice | 25 000 | 1 | 30 | 127 | 773 | 7 | 208 |
| GET /deliveries/{id}/invoice | 25 000 | 1 | 41 | 196 | 656 | 8 | 208 |
| GET /deliveries/business-summary | 30 000 | 1 | 21 | 149 | 489 | 6 | 250 |
| **All** | **130 000** | 1 | 36 | **165** | 773 | 8 | **1083** |

- **130 000 requests, 0 failures**, everything still < 800 ms. Global p99 (165 ms) is 12× under
  the 2 s budget — passes, but this is where load starts to show.
- **~1083 req/s sustained** (2.5× the 2000-driver run), each lifecycle step at a flat 208 req/s.
- **The tail inflated, the median did not.** vs the 2000-driver run: p50 stayed at 1 ms
  everywhere, but global p99 went 7 ms → 165 ms and `POST /deliveries/v2` p95 went 3 ms → 57 ms.
  So a *subset* of requests is now waiting on a shared resource — almost certainly the HikariCP
  pool (10 connections) with ~5000 ÷ 12 ≈ 400 requests in flight.
- **`POST /deliveries/v2` degrades first** (p95 57, p99 207) — the write path pays connection
  acquisition + the `findByVehicleIdAndStartedAt` pre-check + the unique-constraint insert. The
  reads (`GET .../invoice`, `business-summary`) hold up better.
- `InvoicePoller` kept up: 0 failures on the `invoice` / poll steps.
- **This is roughly the knee** — raising the HikariCP pool to 50 removes it (see the section above).

---

## `deliveryloadsimulation-20260827110359818` — 2000 drivers × 5 deliveries

`-Dusers=2000 -Dramp=60 -Ddeliveries=5 -Dpace=12`, ~120 s. Both assertions **passed**.

| Request | count | p50 | p95 | p99 | max | mean | req/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| POST /deliveries/v2 | 10 000 | 2 | 3 | 8 | 434 | 2 | 83 |
| PATCH /deliveries/{id} | 10 000 | 1 | 2 | 6 | 359 | 2 | 83 |
| POST /deliveries/invoice | 10 000 | 1 | 3 | 5 | 363 | 2 | 83 |
| GET /deliveries/{id}/invoice | 10 000 | 1 | 2 | 5 | 337 | 1 | 83 |
| GET /deliveries/business-summary | 12 000 | 2 | 4 | 10 | 355 | 3 | 100 |
| **All** | **52 000** | 1 | 3 | **7** | 434 | 2 | **433** |

- 52 000 requests, 0 failures; global p99 = 7 ms — the app is coasting.
- ~433 req/s sustained, every lifecycle step at 83 req/s.
- `max` sits at 340–430 ms while p99 is 5–10 ms: isolated JVM/connection outliers, not systemic.
- Real concurrency is only ~2000 ÷ 12 ≈ 170 in flight — the driver model paces work like a real
  fleet, so this is not 2000 simultaneous writes.
