package com.ahold.technl.sandbox.delivery

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "deliveries")
open class Delivery(
    @Id
    open val id: UUID,

    @Column(name = "vehicle_id", nullable = false)
    open val vehicleId: String,

    @Column(nullable = false)
    open val address: String,

    @Column(name = "started_at", nullable = false)
    open val startedAt: Instant,

    @Column(name = "finished_at")
    open val finishedAt: Instant?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open val status: DeliveryStatus,

    @Column(name = "created_at", nullable = false)
    open val createdAt: Instant = Instant.now(),
)
