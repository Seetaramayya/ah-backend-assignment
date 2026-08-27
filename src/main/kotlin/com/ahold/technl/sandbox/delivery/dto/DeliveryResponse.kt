package com.ahold.technl.sandbox.delivery.dto

import com.ahold.technl.sandbox.delivery.Delivery
import com.ahold.technl.sandbox.delivery.DeliveryStatus
import java.time.Instant
import java.util.UUID

data class DeliveryResponse(
    val id: UUID,
    val vehicleId: String,
    val address: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: DeliveryStatus,
)

fun Delivery.toResponse(): DeliveryResponse =
    DeliveryResponse(
        id = id,
        vehicleId = vehicleId,
        address = address,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
    )
