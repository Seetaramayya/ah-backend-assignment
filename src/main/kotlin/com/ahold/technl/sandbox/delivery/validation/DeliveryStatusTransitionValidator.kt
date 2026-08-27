package com.ahold.technl.sandbox.delivery.validation

import com.ahold.technl.sandbox.delivery.DeliveryStatus
import com.ahold.technl.sandbox.delivery.dto.CreateDeliveryRequest
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class DeliveryStatusTransitionValidator : ConstraintValidator<ValidDeliveryStatusTransition, CreateDeliveryRequest> {
    override fun isValid(
        value: CreateDeliveryRequest?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || value.status == null) return true
        return when (value.status) {
            DeliveryStatus.IN_PROGRESS -> value.finishedAt == null
            DeliveryStatus.DELIVERED -> value.finishedAt != null
        }
    }
}
