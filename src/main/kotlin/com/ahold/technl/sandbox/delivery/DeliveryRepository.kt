package com.ahold.technl.sandbox.delivery

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface DeliveryRepository : JpaRepository<Delivery, UUID> {
    fun findAllByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
        start: Instant,
        end: Instant,
    ): List<Delivery>

    fun findByVehicleIdAndStartedAt(
        vehicleId: String,
        startedAt: Instant,
    ): Delivery?
}
