package com.ahold.technl.sandbox.delivery

import com.ahold.technl.sandbox.delivery.dto.CompleteDeliveryRequest
import com.ahold.technl.sandbox.delivery.dto.CreateDeliveryRequest
import com.ahold.technl.sandbox.delivery.dto.DeliveryResponse
import com.ahold.technl.sandbox.delivery.dto.StartDeliveryRequest
import com.ahold.technl.sandbox.delivery.dto.toResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class DeliveryService(
    private val deliveryRepository: DeliveryRepository,
) {
    // Backs the deprecated POST /deliveries. Prefer start() + complete().
    @Deprecated("Use start() to begin a delivery and complete() to finish it")
    fun create(request: CreateDeliveryRequest): DeliveryOutcome {
        val delivery =
            Delivery(
                id = UUID.randomUUID(),
                vehicleId = request.vehicleId,
                address = request.address,
                startedAt = requireNotNull(request.startedAt),
                finishedAt = request.finishedAt,
                status = requireNotNull(request.status),
            )
        return insertOrReconcile(delivery, ::reconcileCreate)
    }

    fun start(request: StartDeliveryRequest): DeliveryOutcome {
        val delivery =
            Delivery(
                id = UUID.randomUUID(),
                vehicleId = request.vehicleId,
                address = request.address,
                startedAt = requireNotNull(request.startedAt),
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            )
        return insertOrReconcile(delivery, ::reconcileStart)
    }

    fun complete(
        id: UUID,
        request: CompleteDeliveryRequest,
    ): DeliveryResponse {
        val existing = deliveryRepository.findById(id).orElseThrow { DeliveryNotFoundException(id) }
        if (existing.status != DeliveryStatus.IN_PROGRESS) {
            throw IllegalDeliveryStateException(id, existing.status)
        }
        return markDelivered(existing, requireNotNull(request.finishedAt))
    }

    /**
     * Inserts the delivery. If one already exists for the same `(vehicleId, startedAt)`, the
     * endpoint-specific [onExisting] policy decides whether that is an idempotent retry or a
     * conflict. The `saveAndFlush`/catch covers the race where a concurrent request inserts between
     * the lookup and our own insert.
     */
    private fun insertOrReconcile(
        delivery: Delivery,
        onExisting: (existing: Delivery, requested: Delivery) -> DeliveryOutcome,
    ): DeliveryOutcome {
        deliveryRepository.findByVehicleIdAndStartedAt(delivery.vehicleId, delivery.startedAt)?.let {
            return onExisting(it, delivery)
        }
        return try {
            DeliveryOutcome.Created(deliveryRepository.saveAndFlush(delivery).toResponse())
        } catch (ex: DataIntegrityViolationException) {
            val existing =
                deliveryRepository.findByVehicleIdAndStartedAt(delivery.vehicleId, delivery.startedAt)
                    ?: throw ex
            onExisting(existing, delivery)
        }
    }

    // A repeated start: same address -> hand back the already-started delivery; different -> conflict.
    // Status/finishedAt are not part of a start request, so the stored delivery's lifecycle stage
    // does not matter.
    private fun reconcileStart(
        existing: Delivery,
        requested: Delivery,
    ): DeliveryOutcome =
        if (existing.address == requested.address) {
            DeliveryOutcome.AlreadyExists(existing.toResponse())
        } else {
            throw DeliveryConflictException(existing.id)
        }

    /**
     * `POST /deliveries` is create-or-complete (kept for old clients). A repeat for the same
     * `(vehicleId, startedAt)` is:
     *  - An idempotent retry, when it describes the delivery exactly as stored -> return it;
     *  - A completion, when the stored delivery is `IN_PROGRESS` and the request carries the
     *    `DELIVERED` transition for the same address -> apply it, same as `PATCH /deliveries/{id}`;
     *  - A conflict otherwise (address change, re-timing a finished delivery, reverting to
     *    `IN_PROGRESS`, ...).
     */
    private fun reconcileCreate(
        existing: Delivery,
        requested: Delivery,
    ): DeliveryOutcome {
        val sameDelivery = existing.address == requested.address
        return when {
            sameDelivery &&
                existing.status == requested.status &&
                existing.finishedAt == requested.finishedAt ->
                DeliveryOutcome.AlreadyExists(existing.toResponse())

            sameDelivery &&
                existing.status == DeliveryStatus.IN_PROGRESS &&
                requested.status == DeliveryStatus.DELIVERED ->
                DeliveryOutcome.Updated(markDelivered(existing, requireNotNull(requested.finishedAt)))

            else -> throw DeliveryConflictException(existing.id)
        }
    }

    private fun markDelivered(
        existing: Delivery,
        finishedAt: Instant,
    ): DeliveryResponse {
        if (!finishedAt.isAfter(existing.startedAt)) {
            throw InvalidDeliveryTimeException(existing.id, existing.startedAt, finishedAt)
        }
        val completed =
            Delivery(
                id = existing.id,
                vehicleId = existing.vehicleId,
                address = existing.address,
                startedAt = existing.startedAt,
                finishedAt = finishedAt,
                status = DeliveryStatus.DELIVERED,
                createdAt = existing.createdAt,
            )
        return deliveryRepository.save(completed).toResponse()
    }
}
