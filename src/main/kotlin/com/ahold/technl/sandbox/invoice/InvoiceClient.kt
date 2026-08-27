package com.ahold.technl.sandbox.invoice

import com.ahold.technl.sandbox.invoice.dto.InvoiceApiRequest
import com.ahold.technl.sandbox.invoice.dto.InvoiceApiResponse
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

@Component
class InvoiceClient(
    private val invoiceServiceRestClient: RestClient,
) {
    // Retry (exponential backoff) wraps the circuit breaker, both tuned in application.yml under
    // resilience4j.*.instances.invoiceService. Exhausted retries / an open breaker surface as an
    // exception, which the poller records as a FAILED item.
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    fun sendInvoice(
        deliveryId: UUID,
        address: String,
    ): InvoiceApiResponse =
        invoiceServiceRestClient
            .post()
            .uri("/v1/invoices")
            .body(InvoiceApiRequest(deliveryId, address))
            .retrieve()
            .body(InvoiceApiResponse::class.java)
            ?: error("Invoice service returned an empty response for delivery $deliveryId")

    private companion object {
        const val INSTANCE = "invoiceService"
    }
}
