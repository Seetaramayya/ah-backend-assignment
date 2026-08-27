package com.ahold.technl.sandbox.invoice

import com.ahold.technl.sandbox.delivery.Delivery
import com.ahold.technl.sandbox.delivery.DeliveryRepository
import com.ahold.technl.sandbox.invoice.dto.InvoiceItemResult
import com.ahold.technl.sandbox.invoice.dto.toResult
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class InvoiceService(
    private val deliveryRepository: DeliveryRepository,
    private val invoiceRequestItemRepository: InvoiceRequestItemRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Accepts a batch for invoicing. A delivery that already has an in-flight (`PENDING`) or
     * `SUCCEEDED` item is returned with that item's current state and is *not* queued again — only a
     * delivery with no item, or whose last attempt `FAILED`, is (re)queued as `PENDING` for the
     * poller. Unknown ids come back `FAILED`. The list keeps the same shape; poll
     * `GET /deliveries/{deliveryId}/invoice` for a `PENDING` one.
     */
    fun processBatch(deliveryIds: List<UUID>): List<InvoiceItemResult> {
        val distinctIds = deliveryIds.distinct()
        val latestByDeliveryId = latestItemsByDeliveryId(distinctIds)
        val deliveriesById = fetchDeliveriesToInvoice(distinctIds, latestByDeliveryId)

        val queued = queuePending(distinctIds, latestByDeliveryId, deliveriesById)

        val resultByDeliveryId = queued.resolved + queued.pending.associate { it.deliveryId to it.toResult() }
        return deliveryIds.map { resultByDeliveryId.getValue(it) }
    }

    /** Latest invoicing outcome for a delivery, for clients polling a previously queued item. */
    fun invoiceStatus(deliveryId: UUID): InvoiceItemResult =
        invoiceRequestItemRepository
            .findFirstByDeliveryIdOrderByUpdatedAtDesc(deliveryId)
            ?.toResult()
            ?: throw InvoiceRequestNotFoundException(deliveryId)

    /** deliveryId -> its most recent invoice item (any status), for the deliveries in [deliveryIds]. */
    private fun latestItemsByDeliveryId(deliveryIds: Collection<UUID>): Map<UUID, InvoiceRequestItem> =
        invoiceRequestItemRepository
            .findByDeliveryIdIn(deliveryIds)
            .groupBy { it.deliveryId }
            .mapValues { (_, items) -> items.maxBy { it.updatedAt } }

    private fun fetchDeliveriesToInvoice(
        distinctIds: List<UUID>,
        latestByDeliveryId: Map<UUID, InvoiceRequestItem>,
    ): Map<UUID, Delivery> {
        val toInvoice = distinctIds.filter { needsQueuing(latestByDeliveryId[it]) }
        return if (toInvoice.isEmpty()) {
            emptyMap()
        } else {
            deliveryRepository.findAllById(toInvoice).associateBy { it.id }
        }
    }

    /**
     * In one transaction, classifies each delivery id: an in-flight/succeeded id gets its current
     * state, an unknown id gets `FAILED`, and everything else gets a persisted `PENDING` item.
     */
    private fun queuePending(
        distinctIds: List<UUID>,
        latestByDeliveryId: Map<UUID, InvoiceRequestItem>,
        deliveriesById: Map<UUID, Delivery>,
    ): QueuedBatch {
        val pending = mutableListOf<InvoiceRequestItem>()
        val resolved = mutableMapOf<UUID, InvoiceItemResult>()
        transactionTemplate.execute {
            for (deliveryId in distinctIds) {
                val latest = latestByDeliveryId[deliveryId]
                val delivery = deliveriesById[deliveryId]
                if (latest != null && latest.status != InvoiceItemStatus.FAILED) {
                    resolved[deliveryId] = latest.toResult()
                } else if (delivery == null) {
                    resolved[deliveryId] =
                        InvoiceItemResult(deliveryId, null, InvoiceItemStatus.FAILED, "Delivery $deliveryId not found")
                } else {
                    pending +=
                        invoiceRequestItemRepository.save(
                            InvoiceRequestItem(
                                id = UUID.randomUUID(),
                                deliveryId = deliveryId,
                                status = InvoiceItemStatus.PENDING,
                            ),
                        )
                }
            }
        }
        return QueuedBatch(pending, resolved)
    }

    /** A delivery needs a fresh PENDING item when it has never been invoiced or its last try FAILED. */
    private fun needsQueuing(latest: InvoiceRequestItem?): Boolean =
        latest == null || latest.status == InvoiceItemStatus.FAILED

    private data class QueuedBatch(
        val pending: List<InvoiceRequestItem>,
        val resolved: Map<UUID, InvoiceItemResult>,
    )
}
