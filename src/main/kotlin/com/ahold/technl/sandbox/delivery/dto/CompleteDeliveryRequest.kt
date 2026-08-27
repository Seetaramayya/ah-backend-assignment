package com.ahold.technl.sandbox.delivery.dto

import com.ahold.technl.sandbox.delivery.DeliveryStatus
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import java.time.Instant

/**
 * Body for `PATCH /deliveries/{id}`. The only transition supported today is
 * `IN_PROGRESS -> DELIVERED`, which requires a `finishedAt`. `status` is carried in the body so the
 * caller states its intent explicitly and so further transitions can be added without a new endpoint.
 */
data class CompleteDeliveryRequest(
    @field:NotNull
    val status: DeliveryStatus?,
    @field:NotNull
    val finishedAt: Instant?,
) {
    @get:AssertTrue(message = "status must be DELIVERED")
    val statusSupported: Boolean
        get() = status == null || status == DeliveryStatus.DELIVERED
}
