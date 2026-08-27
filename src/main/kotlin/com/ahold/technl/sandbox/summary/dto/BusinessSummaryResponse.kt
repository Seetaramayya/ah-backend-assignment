package com.ahold.technl.sandbox.summary.dto

data class BusinessSummaryResponse(
    val deliveries: Long,
    val averageMinutesBetweenDeliveryStart: Double?,
)
