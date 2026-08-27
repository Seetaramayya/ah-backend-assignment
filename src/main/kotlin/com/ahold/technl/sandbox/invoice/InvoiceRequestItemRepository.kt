package com.ahold.technl.sandbox.invoice

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InvoiceRequestItemRepository : JpaRepository<InvoiceRequestItem, UUID> {
    fun findByDeliveryIdIn(deliveryIds: Collection<UUID>): List<InvoiceRequestItem>

    fun findByStatusOrderByCreatedAtAsc(
        status: InvoiceItemStatus,
        pageable: Pageable,
    ): List<InvoiceRequestItem>

    fun findFirstByDeliveryIdOrderByUpdatedAtDesc(deliveryId: UUID): InvoiceRequestItem?
}
