package com.ahold.technl.sandbox.error

import java.time.Instant

data class ErrorDetail(
    val field: String?,
    val issue: String,
)

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: List<ErrorDetail> = emptyList(),
    val timestamp: Instant = Instant.now(),
)
