package com.ahold.technl.sandbox.invoice.dto

import com.ahold.technl.sandbox.invoice.validation.MaxBatchSize
import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class InvoiceBatchRequest(
    @field:NotEmpty
    @field:MaxBatchSize
    val deliveryIds: List<UUID> = emptyList(),
)
