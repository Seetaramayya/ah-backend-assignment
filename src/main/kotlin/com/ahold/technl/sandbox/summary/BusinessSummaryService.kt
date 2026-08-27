package com.ahold.technl.sandbox.summary

import com.ahold.technl.sandbox.delivery.DeliveryRepository
import com.ahold.technl.sandbox.summary.dto.BusinessSummaryResponse
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Service
class BusinessSummaryService(
    private val deliveryRepository: DeliveryRepository,
    private val clock: Clock,
) {
    companion object {
        private val AMSTERDAM_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }

    fun getYesterdaySummary(): BusinessSummaryResponse {
        val today = LocalDate.now(clock.withZone(AMSTERDAM_ZONE))
        val yesterday = today.minusDays(1)
        val startOfYesterday = yesterday.atStartOfDay(AMSTERDAM_ZONE).toInstant()
        val startOfToday = today.atStartOfDay(AMSTERDAM_ZONE).toInstant()

        val startedAts =
            deliveryRepository
                .findAllByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(startOfYesterday, startOfToday)
                .map { it.startedAt }

        return BusinessSummaryResponse(
            deliveries = startedAts.size.toLong(),
            averageMinutesBetweenDeliveryStart = averageMinutesBetweenStarts(startedAts),
        )
    }

    fun averageMinutesBetweenStarts(startedAts: List<Instant>): Double? {
        if (startedAts.size < 2) return null
        val sorted = startedAts.sorted()
        val gapsInMinutes = sorted.zipWithNext { a, b -> Duration.between(a, b).toMinutes().toDouble() }
        return gapsInMinutes.average()
    }
}
