package com.ahold.technl.sandbox.delivery

import java.time.Instant
import java.util.UUID

class DeliveryNotFoundException(
    val id: UUID,
) : RuntimeException("Delivery $id was not found")

class IllegalDeliveryStateException(
    val id: UUID,
    val currentStatus: DeliveryStatus,
) : RuntimeException("Delivery $id is $currentStatus and cannot be completed")

/**
 * A delivery already exists for this `(vehicleId, startedAt)` but with different details than the
 * request carried — so this is not an idempotent retry. State changes must go through
 * `PATCH /deliveries/{id}`, not a repeated create.
 */
class DeliveryConflictException(
    val id: UUID,
) : RuntimeException(
        "A delivery already exists for this vehicle and start time (id $id) with different details. " +
            "Use PATCH /deliveries/$id to change its state.",
    )

/** A completion whose `finishedAt` is not strictly after the delivery's `startedAt`. */
class InvalidDeliveryTimeException(
    val id: UUID,
    val startedAt: Instant,
    val finishedAt: Instant,
) : RuntimeException("Delivery $id: finishedAt ($finishedAt) must be after startedAt ($startedAt)")
