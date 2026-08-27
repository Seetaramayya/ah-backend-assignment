package com.ahold.technl.sandbox.delivery.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DeliveryStatusTransitionValidator::class])
annotation class ValidDeliveryStatusTransition(
    val message: String =
        "finishedAt must be null when status is IN_PROGRESS, and must be provided when status is DELIVERED",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
