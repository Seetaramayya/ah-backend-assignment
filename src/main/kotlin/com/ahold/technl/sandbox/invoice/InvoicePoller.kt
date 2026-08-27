package com.ahold.technl.sandbox.invoice

import com.ahold.technl.sandbox.delivery.DeliveryRepository
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Drains `PENDING` invoice items (queued by `POST /deliveries/invoice`) to the third party in the
 * background. Retry + circuit breaker live on [InvoiceClient]; when the breaker is open the item is
 * left `PENDING` for the next tick, otherwise a call that still fails is recorded as `FAILED`.
 */
@Component
class InvoicePoller(
    private val invoiceRequestItemRepository: InvoiceRequestItemRepository,
    private val deliveryRepository: DeliveryRepository,
    private val invoiceClient: InvoiceClient,
    transactionManager: PlatformTransactionManager,
    @Value("\${app.invoice.poll-batch-size}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Scheduled(
        initialDelayString = "\${app.invoice.poll-interval-ms}",
        fixedDelayString = "\${app.invoice.poll-interval-ms}",
    )
    fun scheduledDrain() {
        drainPending()
    }

    fun drainPending() {
        val pending =
            invoiceRequestItemRepository.findByStatusOrderByCreatedAtAsc(
                InvoiceItemStatus.PENDING,
                PageRequest.of(0, batchSize),
            )
        if (pending.isNotEmpty()) {
            val addressByDeliveryId =
                deliveryRepository
                    .findAllById(pending.map { it.deliveryId }.distinct())
                    .associate { it.id to it.address }

            for (item in pending) {
                settle(item, addressByDeliveryId[item.deliveryId])
                item.updatedAt = Instant.now()
                transactionTemplate.execute { invoiceRequestItemRepository.save(item) }
            }
        }

    }

    private fun settle(
        item: InvoiceRequestItem,
        address: String?,
    ) {
        if (address == null) {
            item.status = InvoiceItemStatus.FAILED
            item.errorMessage = "Delivery ${item.deliveryId} no longer exists"
            return
        }
        try {
            val response = invoiceClient.sendInvoice(item.deliveryId, address)
            item.status = InvoiceItemStatus.SUCCEEDED
            item.invoiceId = response.id
        } catch (ex: CallNotPermittedException) {
            log.warn("Invoice service circuit open; leaving delivery {} PENDING for the next tick", item.deliveryId)
        } catch (ex: Exception) {
            log.warn("Failed to invoice delivery {}", item.deliveryId, ex)
            item.status = InvoiceItemStatus.FAILED
            item.errorMessage = ex.message ?: ex.javaClass.simpleName
        }
    }
}
