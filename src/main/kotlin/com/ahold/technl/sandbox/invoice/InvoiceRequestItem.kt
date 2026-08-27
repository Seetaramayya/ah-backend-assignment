package com.ahold.technl.sandbox.invoice

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invoice_request_items")
open class InvoiceRequestItem(
    @Id
    open val id: UUID,
    @Column(name = "delivery_id", nullable = false)
    open val deliveryId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: InvoiceItemStatus,
    @Column(name = "invoice_id")
    open var invoiceId: UUID? = null,
    @Column(name = "error_message")
    open var errorMessage: String? = null,
    @Column(name = "created_at", nullable = false)
    open val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
