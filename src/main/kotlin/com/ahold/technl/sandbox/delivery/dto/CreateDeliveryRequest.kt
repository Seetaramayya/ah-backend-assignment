package com.ahold.technl.sandbox.delivery.dto

import com.ahold.technl.sandbox.delivery.DeliveryStatus
import com.ahold.technl.sandbox.delivery.validation.ValidDeliveryStatusTransition
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

@ValidDeliveryStatusTransition
data class CreateDeliveryRequest(
    @field:NotBlank
    val vehicleId: String,
    @field:NotBlank
    val address: String,
    @field:NotNull
    val startedAt: Instant?,
    val finishedAt: Instant?,
    @field:NotNull
    val status: DeliveryStatus?,
) {
    @get:AssertTrue(message = "finishedAt must be after startedAt")
    val finishedAtAfterStartedAt: Boolean
        get() = startedAt == null || finishedAt == null || finishedAt.isAfter(startedAt)
}
