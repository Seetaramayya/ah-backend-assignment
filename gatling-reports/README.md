# Gatling load-test reports

One HTML report per release, kept out of `target/` (which `mvn clean` wipes). **Retention: keep the
last 5**, delete older ones. Long term this belongs in object storage / a shared drive (S3, GCS,
Confluence, ...) rather than the repo; in-repo is good enough for this assignment.

## How a report is produced

```
docker-compose up --build            # app :8080, mock invoice API :8000
./generate-token.sh                  # writes manual-scripts/.token
./mvnw test-compile gatling:test -Dusers=2000 -Dramp=60 -Ddeliveries=5 -Dpace=12
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

## `deliveryloadsimulation-20260827110359818`

**2000 drivers × 5 deliveries, 12 s apart** (`-Dusers=2000 -Dramp=60 -Ddeliveries=5 -Dpace=12`),
~120 s run. Both assertions **passed**.

| Request | count | p50 | p95 | p99 | max | mean | req/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| POST /deliveries/v2 | 10 000 | 2 | 3 | 8 | 434 | 2 | 83 |
| PATCH /deliveries/{id} | 10 000 | 1 | 2 | 6 | 359 | 2 | 83 |
| POST /deliveries/invoice | 10 000 | 1 | 3 | 5 | 363 | 2 | 83 |
| GET /deliveries/{id}/invoice | 10 000 | 1 | 2 | 5 | 337 | 1 | 83 |
| GET /deliveries/business-summary | 12 000 | 2 | 4 | 10 | 355 | 3 | 100 |
| **All** | **52 000** | 1 | 3 | **7** | 434 | 2 | **433** |

- **52 000 requests, 0 failures.** Global p99 = 7 ms — ~280× under the 2 s budget.
- **~433 req/s sustained**, every lifecycle step at a flat 83 req/s (10 000 / 120 s) — no
  throttling, responses/sec tracked requests/sec the whole run.
- Latency is *lower* than the earlier 500-user single-shot baseline (which had `v2` p99 ≈ 50 ms).
  The reason: `pace(12s)` means only ~2000 / 12 ≈ 170 drivers are mid-request at any instant, so
  real concurrency is modest — which is the point of the driver model. A fleet doing one delivery
  per vehicle every ~12 s is not 2000 simultaneous writes.
- `max` sits at 337–434 ms across every request while p99 is 5–10 ms: those are lone outliers
  (one request each), the usual JVM/connection tail, not systemic.
- `business-summary` has the widest tail (p99 10 ms) — the date-windowed aggregate over
  `deliveries`. Still trivial at this data volume; the one to re-measure once the table is large.
- `InvoicePoller` kept up: no failures on the `invoice` / poll steps, so items were queued and
  `GET /deliveries/{id}/invoice` returned `200` throughout.

**Next step to find the ceiling:** drop `-Dpace` (or raise `-Dusers` / switch to a per-second
arrival rate) until responses/sec stops tracking requests/sec or the p99 assertion fails.
