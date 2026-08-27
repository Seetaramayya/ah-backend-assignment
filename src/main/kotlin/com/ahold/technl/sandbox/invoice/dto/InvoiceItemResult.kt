package com.ahold.technl.sandbox.invoice.dto

import com.ahold.technl.sandbox.invoice.InvoiceItemStatus
import com.ahold.technl.sandbox.invoice.InvoiceRequestItem
import java.util.UUID

data class InvoiceItemResult(
    val deliveryId: UUID,
    val invoiceId: UUID?,
    val status: InvoiceItemStatus,
    val error: String?,
)

fun InvoiceRequestItem.toResult(): InvoiceItemResult =
    InvoiceItemResult(
        deliveryId = deliveryId,
        invoiceId = invoiceId,
        status = status,
        error = errorMessage,
    )
