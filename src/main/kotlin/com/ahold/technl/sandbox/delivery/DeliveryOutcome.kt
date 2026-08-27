package com.ahold.technl.sandbox.delivery

import com.ahold.technl.sandbox.delivery.dto.DeliveryResponse

/**
 * Result of a create/start call. The controller maps [Created] to `201` and the rest to `200`; the
 * distinct cases are kept so the outcome is legible (fresh insert vs. unchanged retry vs. a
 * create-or-complete call that transitioned an existing delivery).
 */
sealed interface DeliveryOutcome {
    val delivery: DeliveryResponse

    data class Created(
        override val delivery: DeliveryResponse,
    ) : DeliveryOutcome

    data class AlreadyExists(
        override val delivery: DeliveryResponse,
    ) : DeliveryOutcome

    data class Updated(
        override val delivery: DeliveryResponse,
    ) : DeliveryOutcome
}
