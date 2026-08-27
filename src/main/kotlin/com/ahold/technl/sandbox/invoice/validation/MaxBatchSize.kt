package com.ahold.technl.sandbox.invoice.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Caps the size of a collection at `app.invoice.max-delivery-ids`. A config-backed constraint rather
 * than `@Size(max = …)` because the limit is an operational knob, not a compile-time invariant.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxBatchSizeValidator::class])
annotation class MaxBatchSize(
    val message: String = "too many delivery ids per request",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
