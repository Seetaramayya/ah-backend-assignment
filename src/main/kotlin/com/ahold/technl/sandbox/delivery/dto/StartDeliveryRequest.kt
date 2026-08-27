package com.ahold.technl.sandbox.delivery.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

/**
 * Body for `POST /deliveries/v2`. A delivery can only be *started*, so it is always created with
 * status `IN_PROGRESS` and no `finishedAt`; completing it is a separate `PATCH /deliveries/{id}`.
 */
data class StartDeliveryRequest(
    @field:NotBlank
    val vehicleId: String,
    @field:NotBlank
    val address: String,
    @field:NotNull
    val startedAt: Instant?,
)
