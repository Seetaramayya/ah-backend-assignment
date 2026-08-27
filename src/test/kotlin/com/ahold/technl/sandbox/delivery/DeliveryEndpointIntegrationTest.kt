package com.ahold.technl.sandbox.delivery

import com.ahold.technl.sandbox.delivery.dto.CreateDeliveryRequest
import com.ahold.technl.sandbox.delivery.dto.DeliveryResponse
import com.ahold.technl.sandbox.delivery.dto.StartDeliveryRequest
import com.ahold.technl.sandbox.error.ErrorResponse
import com.ahold.technl.sandbox.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.Instant
import java.util.UUID

class DeliveryEndpointIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `creates an IN_PROGRESS delivery`() {
        val request =
            CreateDeliveryRequest(
                vehicleId = "AHV-589",
                address = "Example street 15A",
                startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            )

        val response =
            restTemplate.postForEntity(
                "/deliveries",
                HttpEntity(request, authHeaders()),
                DeliveryResponse::class.java,
            )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body?.id)
        assertEquals(DeliveryStatus.IN_PROGRESS, response.body?.status)
        assertEquals(request.address, response.body?.address)
    }

    @Test
    fun `rejects IN_PROGRESS delivery with a finishedAt`() {
        val request =
            CreateDeliveryRequest(
                vehicleId = "AHV-589",
                address = "Example street 15A",
                startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
                finishedAt = Instant.now(),
                status = DeliveryStatus.IN_PROGRESS,
            )

        val response =
            restTemplate.postForEntity(
                "/deliveries",
                HttpEntity(request, authHeaders()),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.code)
    }

    @Test
    fun `rejects requests without a bearer token`() {
        val request =
            CreateDeliveryRequest(
                vehicleId = "AHV-589",
                address = "Example street 15A",
                startedAt = Instant.now(),
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            )

        val response = restTemplate.postForEntity("/deliveries", HttpEntity(request), String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `deprecated POST deliveries still works and flags the deprecation`() {
        val request =
            CreateDeliveryRequest(
                vehicleId = "AHV-589",
                address = "Example street 15A",
                startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            )

        val response =
            restTemplate.postForEntity(
                "/deliveries",
                HttpEntity(request, authHeaders()),
                DeliveryResponse::class.java,
            )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("true", response.headers.getFirst("Deprecation"))
    }

    @Test
    fun `retrying POST deliveries with the same payload returns the existing delivery, not a duplicate`() {
        val request =
            CreateDeliveryRequest(
                vehicleId = "AHV-777",
                address = "Retry road 1",
                startedAt = Instant.parse("2023-10-09T09:00:00Z"),
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            )
        val entity = HttpEntity(request, authHeaders())

        val first = restTemplate.postForEntity("/deliveries", entity, DeliveryResponse::class.java)
        val second = restTemplate.postForEntity("/deliveries", entity, DeliveryResponse::class.java)

        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(first.body?.id, second.body?.id)
        assertEquals(1, deliveryRepository.count())
    }

    @Test
    fun `POST deliveries completes an existing in-progress delivery (create-or-complete)`() {
        val started = Instant.parse("2023-10-09T09:00:00Z")
        val create =
            CreateDeliveryRequest(
                vehicleId = "AHV-778",
                address = "Complete close 2",
                startedAt = started,
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            )
        restTemplate.postForEntity("/deliveries", HttpEntity(create, authHeaders()), DeliveryResponse::class.java)

        val finishedAt = started.plusSeconds(300)
        val complete = create.copy(status = DeliveryStatus.DELIVERED, finishedAt = finishedAt)
        val response =
            restTemplate.postForEntity(
                "/deliveries",
                HttpEntity(complete, authHeaders()),
                DeliveryResponse::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(DeliveryStatus.DELIVERED, response.body?.status)
        assertEquals(finishedAt, response.body?.finishedAt)
        val stored = deliveryRepository.findByVehicleIdAndStartedAt("AHV-778", started)!!
        assertEquals(DeliveryStatus.DELIVERED, stored.status)
        assertEquals(finishedAt, stored.finishedAt)

        // completing again with the same details is idempotent
        val again =
            restTemplate.postForEntity("/deliveries", HttpEntity(complete, authHeaders()), DeliveryResponse::class.java)
        assertEquals(HttpStatus.OK, again.statusCode)
        assertEquals(DeliveryStatus.DELIVERED, again.body?.status)
    }

    @Test
    fun `POST deliveries with the same key but a non-transition change is a 409`() {
        val started = Instant.parse("2023-10-09T09:00:00Z")
        val create =
            CreateDeliveryRequest(
                vehicleId = "AHV-779",
                address = "Conflict close 2",
                startedAt = started,
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            )
        restTemplate.postForEntity("/deliveries", HttpEntity(create, authHeaders()), DeliveryResponse::class.java)

        val response =
            restTemplate.postForEntity(
                "/deliveries",
                HttpEntity(create.copy(address = "Different street 9"), authHeaders()),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("DELIVERY_CONFLICT", response.body?.code)
        val stored = deliveryRepository.findByVehicleIdAndStartedAt("AHV-779", started)!!
        assertEquals("Conflict close 2", stored.address)
        assertEquals(DeliveryStatus.IN_PROGRESS, stored.status)
    }

    @Test
    fun `POST deliveries v2 starts an IN_PROGRESS delivery`() {
        val response = startDelivery()

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body?.id)
        assertEquals(DeliveryStatus.IN_PROGRESS, response.body?.status)
        assertNull(response.body?.finishedAt)
    }

    @Test
    fun `retrying POST deliveries v2 with the same payload returns the existing delivery, not a duplicate`() {
        val first = startDelivery()
        val second = startDelivery()

        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(first.body?.id, second.body?.id)
        assertEquals(1, deliveryRepository.count())
    }

    @Test
    fun `POST deliveries v2 with the same key but a different address is a 409`() {
        val started = Instant.parse("2023-10-09T10:00:00Z")
        val first =
            restTemplate.postForEntity(
                "/deliveries/v2",
                HttpEntity(StartDeliveryRequest("AHV-999", "First avenue 1", started), authHeaders()),
                DeliveryResponse::class.java,
            )
        assertEquals(HttpStatus.CREATED, first.statusCode)

        val conflict =
            restTemplate.postForEntity(
                "/deliveries/v2",
                HttpEntity(StartDeliveryRequest("AHV-999", "Second avenue 2", started), authHeaders()),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.CONFLICT, conflict.statusCode)
        assertEquals("DELIVERY_CONFLICT", conflict.body?.code)
    }

    @Test
    fun `POST deliveries v2 is idempotent even after the delivery has been completed`() {
        val created = startDelivery().body!!
        restTemplate.exchange(
            "/deliveries/${created.id}",
            HttpMethod.PATCH,
            HttpEntity(
                mapOf("status" to "DELIVERED", "finishedAt" to Instant.parse("2023-10-09T14:00:00Z").toString()),
                authHeaders(),
            ),
            DeliveryResponse::class.java,
        )

        val retry = startDelivery()

        assertEquals(HttpStatus.OK, retry.statusCode)
        assertEquals(created.id, retry.body?.id)
        assertEquals(DeliveryStatus.DELIVERED, retry.body?.status)
    }

    @Test
    fun `PATCH deliveries completes an in-progress delivery`() {
        val id = startDelivery().body!!.id
        val finishedAt = Instant.parse("2023-10-09T13:30:00Z")

        val response =
            restTemplate.exchange(
                "/deliveries/$id",
                HttpMethod.PATCH,
                HttpEntity(mapOf("status" to "DELIVERED", "finishedAt" to finishedAt.toString()), authHeaders()),
                DeliveryResponse::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(DeliveryStatus.DELIVERED, response.body?.status)
        assertEquals(finishedAt, response.body?.finishedAt)
    }

    @Test
    fun `PATCH deliveries returns 404 for an unknown id`() {
        val response =
            restTemplate.exchange(
                "/deliveries/${UUID.randomUUID()}",
                HttpMethod.PATCH,
                HttpEntity(mapOf("status" to "DELIVERED", "finishedAt" to Instant.now().toString()), authHeaders()),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("DELIVERY_NOT_FOUND", response.body?.code)
    }

    @Test
    fun `PATCH deliveries returns 409 when the delivery is already delivered`() {
        val id = startDelivery().body!!.id
        val body = mapOf("status" to "DELIVERED", "finishedAt" to Instant.parse("2023-10-09T13:30:00Z").toString())
        restTemplate.exchange("/deliveries/$id", HttpMethod.PATCH, HttpEntity(body, authHeaders()), DeliveryResponse::class.java)

        val response =
            restTemplate.exchange(
                "/deliveries/$id",
                HttpMethod.PATCH,
                HttpEntity(body, authHeaders()),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("ILLEGAL_DELIVERY_STATE", response.body?.code)
    }

    @Test
    fun `PATCH deliveries rejects a non-DELIVERED status`() {
        val id = startDelivery().body!!.id

        val response =
            restTemplate.exchange(
                "/deliveries/$id",
                HttpMethod.PATCH,
                HttpEntity(mapOf("status" to "IN_PROGRESS", "finishedAt" to Instant.now().toString()), authHeaders()),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.code)
    }

    @Test
    fun `PATCH deliveries rejects a finishedAt that is not after startedAt`() {
        val delivery = startDelivery().body!!

        val response =
            restTemplate.exchange(
                "/deliveries/${delivery.id}",
                HttpMethod.PATCH,
                HttpEntity(
                    mapOf("status" to "DELIVERED", "finishedAt" to delivery.startedAt.minusSeconds(60).toString()),
                    authHeaders(),
                ),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_DELIVERY_TIME", response.body?.code)
        assertEquals(DeliveryStatus.IN_PROGRESS, deliveryRepository.findById(delivery.id).get().status)
    }

    @Test
    fun `POST deliveries rejects a DELIVERED create whose finishedAt is not after startedAt`() {
        val startedAt = Instant.parse("2023-10-09T12:00:00Z")
        val request =
            CreateDeliveryRequest(
                vehicleId = "AHV-780",
                address = "Backdated blvd 1",
                startedAt = startedAt,
                finishedAt = startedAt,
                status = DeliveryStatus.DELIVERED,
            )

        val response =
            restTemplate.postForEntity("/deliveries", HttpEntity(request, authHeaders()), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.code)
    }

    private fun startDelivery(): ResponseEntity<DeliveryResponse> {
        val request =
            StartDeliveryRequest(
                vehicleId = "AHV-589",
                address = "Example street 15A",
                startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
            )
        return restTemplate.postForEntity(
            "/deliveries/v2",
            HttpEntity(request, authHeaders()),
            DeliveryResponse::class.java,
        )
    }
}
