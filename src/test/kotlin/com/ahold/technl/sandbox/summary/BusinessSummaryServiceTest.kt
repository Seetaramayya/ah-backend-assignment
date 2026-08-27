package com.ahold.technl.sandbox.summary

import com.ahold.technl.sandbox.delivery.Delivery
import com.ahold.technl.sandbox.delivery.DeliveryRepository
import com.ahold.technl.sandbox.delivery.DeliveryStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class BusinessSummaryServiceTest {
    private val deliveryRepository = mockk<DeliveryRepository>()
    private val amsterdamZone: ZoneId = ZoneId.of("Europe/Amsterdam")

    private fun serviceAt(instant: Instant): BusinessSummaryService =
        BusinessSummaryService(deliveryRepository, Clock.fixed(instant, ZoneId.of("UTC")))

    private fun delivery(startedAt: Instant): Delivery =
        Delivery(
            id = UUID.randomUUID(),
            vehicleId = "AHV-1",
            address = "Example street 1",
            startedAt = startedAt,
            finishedAt = null,
            status = DeliveryStatus.IN_PROGRESS,
        )

    @Test
    fun `no deliveries started yesterday returns null average`() {
        every {
            deliveryRepository.findAllByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(any(), any())
        } returns emptyList()

        val summary = serviceAt(ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, amsterdamZone).toInstant()).getYesterdaySummary()

        assertEquals(0L, summary.deliveries)
        assertNull(summary.averageMinutesBetweenDeliveryStart)
    }

    @Test
    fun `single delivery started yesterday returns null average`() {
        val startedAt = ZonedDateTime.of(2026, 6, 14, 8, 0, 0, 0, amsterdamZone).toInstant()
        every {
            deliveryRepository.findAllByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(any(), any())
        } returns listOf(delivery(startedAt))

        val summary = serviceAt(ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, amsterdamZone).toInstant()).getYesterdaySummary()

        assertEquals(1L, summary.deliveries)
        assertNull(summary.averageMinutesBetweenDeliveryStart)
    }

    @Test
    fun `computes average minutes between starts per README example`() {
        val day = ZonedDateTime.of(2026, 6, 14, 0, 0, 0, 0, amsterdamZone)
        val starts = listOf(day.withHour(1), day.withHour(3), day.withHour(9)).map { it.toInstant() }
        every {
            deliveryRepository.findAllByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(any(), any())
        } returns starts.map { delivery(it) }

        val summary = serviceAt(ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, amsterdamZone).toInstant()).getYesterdaySummary()

        assertEquals(3L, summary.deliveries)
        assertEquals(240.0, summary.averageMinutesBetweenDeliveryStart)
    }

    @Test
    fun `day boundary respects Amsterdam DST transition`() {
        val startSlot = slot<Instant>()
        val endSlot = slot<Instant>()
        every {
            deliveryRepository.findAllByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
                capture(startSlot),
                capture(endSlot),
            )
        } returns emptyList()

        // 2026-03-29 is the Europe/Amsterdam DST start date (clocks jump forward, a 23-hour day).
        serviceAt(ZonedDateTime.of(2026, 3, 30, 10, 0, 0, 0, amsterdamZone).toInstant()).getYesterdaySummary()

        val expectedStart = ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, amsterdamZone).toInstant()
        val expectedEnd = ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, amsterdamZone).toInstant()
        assertEquals(expectedStart, startSlot.captured)
        assertEquals(expectedEnd, endSlot.captured)
        assertEquals(23L, Duration.between(expectedStart, expectedEnd).toHours())
    }
}
