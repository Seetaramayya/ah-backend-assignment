package com.ahold.technl.sandbox.invoice.dto

import java.util.UUID

data class InvoiceApiRequest(
    val deliveryId: UUID,
    val address: String,
)

data class InvoiceApiResponse(
    val id: UUID,
    val sent: Boolean,
)
