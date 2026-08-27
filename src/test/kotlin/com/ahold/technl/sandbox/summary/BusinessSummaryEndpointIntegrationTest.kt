package com.ahold.technl.sandbox.summary

import com.ahold.technl.sandbox.delivery.Delivery
import com.ahold.technl.sandbox.delivery.DeliveryStatus
import com.ahold.technl.sandbox.summary.dto.BusinessSummaryResponse
import com.ahold.technl.sandbox.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class BusinessSummaryEndpointIntegrationTest : AbstractIntegrationTest() {
    private fun saveDelivery(startedAt: Instant) {
        deliveryRepository.save(
            Delivery(
                id = UUID.randomUUID(),
                vehicleId = "AHV-589",
                address = "Example street 15A",
                startedAt = startedAt,
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            ),
        )
    }

    @Test
    fun `summarises deliveries started yesterday in Amsterdam time`() {
        val amsterdamZone = ZoneId.of("Europe/Amsterdam")
        val yesterday = ZonedDateTime.now(amsterdamZone).minusDays(1).toLocalDate()
        saveDelivery(yesterday.atTime(1, 0).atZone(amsterdamZone).toInstant())
        saveDelivery(yesterday.atTime(3, 0).atZone(amsterdamZone).toInstant())
        saveDelivery(yesterday.atTime(9, 0).atZone(amsterdamZone).toInstant())

        val response =
            restTemplate.exchange(
                "/deliveries/business-summary",
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders()),
                BusinessSummaryResponse::class.java,
            )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(3L, response.body?.deliveries)
        assertEquals(240.0, response.body?.averageMinutesBetweenDeliveryStart)
    }
}
