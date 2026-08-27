package com.ahold.technl.sandbox.loadtest

import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.core.CoreDsl.pace
import io.gatling.javaapi.core.CoreDsl.rampUsers
import io.gatling.javaapi.core.CoreDsl.repeat
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * Load test for the delivery + invoicing flow. Runs against a *running* app, so it is not part of
 * `./mvnw test` / CI. `*Simulation` is not picked up by Surefire and does not count toward coverage.
 *
 *   docker-compose up --build              # app on :8080, mock invoice API on :8000
 *   ./generate-token.sh                    # writes manual-scripts/.token (read automatically below)
 *   ./mvnw test-compile gatling:test \
 *       -Dusers=2000 -Dramp=60 -Ddeliveries=5 -Dpace=12 -Dbase.url=http://localhost:8080
 *
 * Each virtual user is a "driver" (one `vehicleId`) that creates `deliveries` deliveries, one every
 * `pace` seconds, each: start -> complete -> invoice -> poll. `users` drivers are injected over
 * `ramp` seconds. Defaults: 20 drivers x 5 deliveries, 12 s apart.
 *
 * HTML report: target/gatling/deliveryloadsimulation-<timestamp>/index.html
 */
class DeliveryLoadSimulation : Simulation() {
    private val baseUrl: String = System.getProperty("base.url", "http://localhost:8080")
    private val users: Int = Integer.getInteger("users", 20)
    private val rampSeconds: Long = Integer.getInteger("ramp", 60).toLong()
    private val deliveriesPerDriver: Int = Integer.getInteger("deliveries", 5)
    private val paceSeconds: Long = Integer.getInteger("pace", 12).toLong()

    private val runSeconds: Long = rampSeconds + deliveriesPerDriver * paceSeconds

    private val token: String =
        (System.getProperty("token")?.takeIf { it.isNotBlank() })
            ?: runCatching { File("manual-scripts/.token").readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: error("No JWT: pass -Dtoken=<jwt> or run ./generate-token.sh first")

    private val httpProtocol =
        http.baseUrl(baseUrl)
            .authorizationHeader("Bearer $token")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")

    private val oneDelivery =
        exec { session ->
            // Same vehicle, a distinct past startedAt each time -> a new (vehicleId, startedAt) row.
            session
                .set("startedAt", Instant.now().minusSeconds(ThreadLocalRandom.current().nextLong(60, 7200)).toString())
                .set("finishedAt", Instant.now().toString())
        }
            .exec(
                http("POST /deliveries/v2")
                    .post("/deliveries/v2")
                    .body(
                        StringBody(
                            """{"vehicleId":"#{vehicleId}","address":"Load street 1","startedAt":"#{startedAt}"}""",
                        ),
                    )
                    .check(status().shouldBe(201))
                    .check(jsonPath("\$.id").saveAs("deliveryId")),
            )
            .exec(
                http("PATCH /deliveries/{id}")
                    .patch("/deliveries/#{deliveryId}")
                    .body(StringBody("""{"status":"DELIVERED","finishedAt":"#{finishedAt}"}"""))
                    .check(status().shouldBe(200)),
            )
            .exec(
                http("POST /deliveries/invoice")
                    .post("/deliveries/invoice")
                    .body(StringBody("""{"deliveryIds":["#{deliveryId}"]}"""))
                    .check(status().shouldBe(202)),
            )
            .pause(Duration.ofMillis(500))
            .exec(
                http("GET /deliveries/{id}/invoice")
                    .get("/deliveries/#{deliveryId}/invoice")
                    .check(status().shouldBe(200)),
            )
            .pace(Duration.ofSeconds(paceSeconds))

    private val driver =
        scenario("driver: $deliveriesPerDriver deliveries")
            .exec { it.set("vehicleId", "AHV-LOAD-${UUID.randomUUID()}") }
            .repeat(deliveriesPerDriver).on(oneDelivery)

    private val summaryRps: Double = maxOf(1.0, users / 20.0)

    private val businessSummary =
        scenario("GET /deliveries/business-summary")
            .exec(
                http("GET /deliveries/business-summary")
                    .get("/deliveries/business-summary")
                    .check(status().shouldBe(200)),
            )

    init {
        setUp(
            driver.injectOpen(rampUsers(users).during(Duration.ofSeconds(rampSeconds))),
            businessSummary.injectOpen(
                constantUsersPerSec(summaryRps).during(Duration.ofSeconds(runSeconds)),
            ),
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().percentile(99.0).lt(2000),
                global().failedRequests().percent().lt(1.0),
            )
    }
}
