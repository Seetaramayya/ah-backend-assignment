package com.ahold.technl.sandbox.invoice

import com.ahold.technl.sandbox.delivery.Delivery
import com.ahold.technl.sandbox.delivery.DeliveryRepository
import com.ahold.technl.sandbox.delivery.DeliveryStatus
import com.ahold.technl.sandbox.invoice.dto.InvoiceApiResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.UUID

class InvoicePollerTest {
    private val invoiceRequestItemRepository = mockk<InvoiceRequestItemRepository>()
    private val deliveryRepository = mockk<DeliveryRepository>()
    private val invoiceClient = mockk<InvoiceClient>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private lateinit var poller: InvoicePoller

    @BeforeEach
    fun setUp() {
        every { invoiceRequestItemRepository.save(any()) } answers { firstArg() }
        poller = InvoicePoller(invoiceRequestItemRepository, deliveryRepository, invoiceClient, transactionManager, 100)
    }

    private fun pendingItem(deliveryId: UUID): InvoiceRequestItem =
        InvoiceRequestItem(
            id = UUID.randomUUID(),
            deliveryId = deliveryId,
            status = InvoiceItemStatus.PENDING,
        )

    private fun delivery(id: UUID): Delivery =
        Delivery(
            id = id,
            vehicleId = "AHV-589",
            address = "Example street 15A",
            startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
            finishedAt = null,
            status = DeliveryStatus.IN_PROGRESS,
        )

    @Test
    fun `sends a pending item and records the returned invoice id`() {
        val deliveryId = UUID.randomUUID()
        val invoiceId = UUID.randomUUID()
        val item = pendingItem(deliveryId)
        every {
            invoiceRequestItemRepository.findByStatusOrderByCreatedAtAsc(InvoiceItemStatus.PENDING, any<Pageable>())
        } returns listOf(item)
        every { deliveryRepository.findAllById(any()) } returns listOf(delivery(deliveryId))
        every { invoiceClient.sendInvoice(deliveryId, "Example street 15A") } returns InvoiceApiResponse(invoiceId, true)

        poller.drainPending()

        assertEquals(InvoiceItemStatus.SUCCEEDED, item.status)
        assertEquals(invoiceId, item.invoiceId)
        verify { invoiceRequestItemRepository.save(item) }
    }

    @Test
    fun `marks a pending item failed when the invoice client throws`() {
        val deliveryId = UUID.randomUUID()
        val item = pendingItem(deliveryId)
        every {
            invoiceRequestItemRepository.findByStatusOrderByCreatedAtAsc(InvoiceItemStatus.PENDING, any<Pageable>())
        } returns listOf(item)
        every { deliveryRepository.findAllById(any()) } returns listOf(delivery(deliveryId))
        every { invoiceClient.sendInvoice(deliveryId, "Example street 15A") } throws RuntimeException("service unavailable")

        poller.drainPending()

        assertEquals(InvoiceItemStatus.FAILED, item.status)
        assertEquals("service unavailable", item.errorMessage)
    }

    @Test
    fun `does nothing when there are no pending items`() {
        every {
            invoiceRequestItemRepository.findByStatusOrderByCreatedAtAsc(InvoiceItemStatus.PENDING, any<Pageable>())
        } returns emptyList()

        poller.drainPending()

        verify(exactly = 0) { deliveryRepository.findAllById(any()) }
        verify(exactly = 0) { invoiceClient.sendInvoice(any(), any()) }
        verify(exactly = 0) { invoiceRequestItemRepository.save(any()) }
    }
}
