package com.ahold.technl.sandbox.invoice

import com.ahold.technl.sandbox.invoice.dto.InvoiceBatchRequest
import com.ahold.technl.sandbox.invoice.dto.InvoiceItemResult
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/deliveries")
class InvoiceController(
    private val invoiceService: InvoiceService,
) {
    /**
     * Queues a batch for invoicing. Already-invoiced and unknown ids are resolved in the response;
     * the rest come back `PENDING` (no `invoiceId` yet) and are sent by the background poller — poll
     * `GET /deliveries/{deliveryId}/invoice` for those.
     */
    @PostMapping("/invoice")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun invoice(
        @Valid @RequestBody request: InvoiceBatchRequest,
    ): List<InvoiceItemResult> = invoiceService.processBatch(request.deliveryIds)

    @GetMapping("/{deliveryId}/invoice")
    fun invoiceStatus(
        @PathVariable deliveryId: UUID,
    ): InvoiceItemResult = invoiceService.invoiceStatus(deliveryId)
}
