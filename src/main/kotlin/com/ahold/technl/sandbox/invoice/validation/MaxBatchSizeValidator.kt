package com.ahold.technl.sandbox.invoice.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MaxBatchSizeValidator(
    @Value("\${app.invoice.max-delivery-ids:100}") private val max: Int,
) : ConstraintValidator<MaxBatchSize, Collection<*>?> {
    override fun isValid(
        value: Collection<*>?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || value.size <= max) return true
        context.disableDefaultConstraintViolation()
        context
            .buildConstraintViolationWithTemplate("at most $max delivery ids per request")
            .addConstraintViolation()
        return false
    }
}
