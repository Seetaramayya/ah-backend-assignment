package com.ahold.technl.sandbox.delivery

import com.ahold.technl.sandbox.delivery.dto.CreateDeliveryRequest
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DeliveryStatusTransitionValidatorTest {
    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    private fun request(
        status: DeliveryStatus,
        finishedAt: Instant?,
    ) = CreateDeliveryRequest(
        vehicleId = "AHV-589",
        address = "Example street 15A",
        startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
        finishedAt = finishedAt,
        status = status,
    )

    @Test
    fun `IN_PROGRESS with null finishedAt is valid`() {
        val violations = validator.validate(request(DeliveryStatus.IN_PROGRESS, null))
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `IN_PROGRESS with finishedAt is invalid`() {
        val violations = validator.validate(request(DeliveryStatus.IN_PROGRESS, Instant.now()))
        assertEquals(1, violations.size)
    }

    @Test
    fun `DELIVERED with finishedAt is valid`() {
        val violations = validator.validate(request(DeliveryStatus.DELIVERED, Instant.now()))
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `DELIVERED with null finishedAt is invalid`() {
        val violations = validator.validate(request(DeliveryStatus.DELIVERED, null))
        assertEquals(1, violations.size)
    }
}
