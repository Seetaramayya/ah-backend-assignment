package com.ahold.technl.sandbox.invoice

import java.util.UUID

/** No invoice item has ever been queued for this delivery, so there is nothing to report on. */
class InvoiceRequestNotFoundException(
    val deliveryId: UUID,
) : RuntimeException("No invoice request found for delivery $deliveryId")
