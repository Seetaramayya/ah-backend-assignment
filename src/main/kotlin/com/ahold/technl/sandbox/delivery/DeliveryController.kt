package com.ahold.technl.sandbox.delivery

import com.ahold.technl.sandbox.delivery.dto.CompleteDeliveryRequest
import com.ahold.technl.sandbox.delivery.dto.CreateDeliveryRequest
import com.ahold.technl.sandbox.delivery.dto.DeliveryResponse
import com.ahold.technl.sandbox.delivery.dto.StartDeliveryRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/deliveries")
class DeliveryController(
    private val deliveryService: DeliveryService,
) {
    /**
     * Deprecated: this endpoint mixes "start a delivery" and "record a completed delivery" in one
     * call. Use [start] (`POST /deliveries/v2`) to start one and [complete] (`PATCH /deliveries/{id}`)
     * to finish it. Kept unchanged for backward compatibility; responses carry a `Deprecation` header.
     */
    @Deprecated("Use POST /deliveries/v2 + PATCH /deliveries/{id}")
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateDeliveryRequest,
    ): ResponseEntity<DeliveryResponse> {
        val outcome = deliveryService.create(request)
        return ResponseEntity
            .status(outcome.httpStatus())
            .header("Deprecation", "true")
            .header("Link", "</deliveries/v2>; rel=\"successor-version\"")
            .body(outcome.delivery)
    }

    @PostMapping("/v2")
    fun start(
        @Valid @RequestBody request: StartDeliveryRequest,
    ): ResponseEntity<DeliveryResponse> {
        val outcome = deliveryService.start(request)
        return ResponseEntity.status(outcome.httpStatus()).body(outcome.delivery)
    }

    @PatchMapping("/{id}")
    fun complete(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CompleteDeliveryRequest,
    ): DeliveryResponse = deliveryService.complete(id, request)

    // Fresh insert -> 201; a retried create that matched an existing delivery -> 200.
    private fun DeliveryOutcome.httpStatus(): HttpStatus =
        if (this is DeliveryOutcome.Created) HttpStatus.CREATED else HttpStatus.OK
}
