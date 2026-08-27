package com.ahold.technl.sandbox.invoice

import com.ahold.technl.sandbox.delivery.Delivery
import com.ahold.technl.sandbox.delivery.DeliveryRepository
import com.ahold.technl.sandbox.delivery.DeliveryStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.UUID

class InvoiceServiceTest {
    private val deliveryRepository = mockk<DeliveryRepository>()
    private val invoiceRequestItemRepository = mockk<InvoiceRequestItemRepository>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private lateinit var service: InvoiceService

    @BeforeEach
    fun setUp() {
        every { invoiceRequestItemRepository.save(any()) } answers { firstArg() }
        every { invoiceRequestItemRepository.findByDeliveryIdIn(any()) } returns emptyList()

        service = InvoiceService(deliveryRepository, invoiceRequestItemRepository, transactionManager)
    }

    private fun delivery(id: UUID): Delivery =
        Delivery(
            id = id,
            vehicleId = "AHV-589",
            address = "Example street 15A",
            startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
            finishedAt = null,
            status = DeliveryStatus.IN_PROGRESS,
        )

    private fun item(
        deliveryId: UUID,
        status: InvoiceItemStatus,
        invoiceId: UUID? = null,
        error: String? = null,
    ): InvoiceRequestItem =
        InvoiceRequestItem(
            id = UUID.randomUUID(),
            deliveryId = deliveryId,
            status = status,
            invoiceId = invoiceId,
            errorMessage = error,
        )

    @Test
    fun `unknown delivery id is reported as failed without queuing anything`() {
        val deliveryId = UUID.randomUUID()
        every { deliveryRepository.findAllById(any()) } returns emptyList()

        val results = service.processBatch(listOf(deliveryId))

        assertEquals(1, results.size)
        assertEquals(InvoiceItemStatus.FAILED, results[0].status)
        assertNull(results[0].invoiceId)
        assertNotNull(results[0].error)
        verify(exactly = 0) { invoiceRequestItemRepository.save(any()) }
    }

    @Test
    fun `already invoiced delivery is returned as succeeded without a delivery lookup or a new item`() {
        val deliveryId = UUID.randomUUID()
        val existingInvoiceId = UUID.randomUUID()
        every {
            invoiceRequestItemRepository.findByDeliveryIdIn(any())
        } returns listOf(item(deliveryId, InvoiceItemStatus.SUCCEEDED, existingInvoiceId))

        val results = service.processBatch(listOf(deliveryId))

        assertEquals(InvoiceItemStatus.SUCCEEDED, results[0].status)
        assertEquals(existingInvoiceId, results[0].invoiceId)
        verify(exactly = 0) { deliveryRepository.findAllById(any()) }
        verify(exactly = 0) { invoiceRequestItemRepository.save(any()) }
    }

    @Test
    fun `a delivery already PENDING is returned as-is and not queued again`() {
        val deliveryId = UUID.randomUUID()
        every {
            invoiceRequestItemRepository.findByDeliveryIdIn(any())
        } returns listOf(item(deliveryId, InvoiceItemStatus.PENDING))

        val results = service.processBatch(listOf(deliveryId))

        assertEquals(InvoiceItemStatus.PENDING, results[0].status)
        verify(exactly = 0) { deliveryRepository.findAllById(any()) }
        verify(exactly = 0) { invoiceRequestItemRepository.save(any()) }
    }

    @Test
    fun `a delivery whose last attempt FAILED is queued again`() {
        val deliveryId = UUID.randomUUID()
        every {
            invoiceRequestItemRepository.findByDeliveryIdIn(any())
        } returns listOf(item(deliveryId, InvoiceItemStatus.FAILED, error = "boom"))
        every { deliveryRepository.findAllById(any()) } returns listOf(delivery(deliveryId))

        val results = service.processBatch(listOf(deliveryId))

        assertEquals(InvoiceItemStatus.PENDING, results[0].status)
        verify(exactly = 1) { invoiceRequestItemRepository.save(match { it.status == InvoiceItemStatus.PENDING }) }
    }

    @Test
    fun `a new delivery id is queued as pending`() {
        val deliveryId = UUID.randomUUID()
        every { deliveryRepository.findAllById(any()) } returns listOf(delivery(deliveryId))

        val results = service.processBatch(listOf(deliveryId))

        assertEquals(InvoiceItemStatus.PENDING, results[0].status)
        assertNull(results[0].invoiceId)
        verify(exactly = 1) { invoiceRequestItemRepository.save(match { it.status == InvoiceItemStatus.PENDING }) }
    }

    @Test
    fun `duplicate delivery ids in the same batch queue one item and reflect it for each occurrence`() {
        val deliveryId = UUID.randomUUID()
        every { deliveryRepository.findAllById(any()) } returns listOf(delivery(deliveryId))

        val results = service.processBatch(listOf(deliveryId, deliveryId))

        assertEquals(2, results.size)
        assertEquals(InvoiceItemStatus.PENDING, results[0].status)
        assertEquals(InvoiceItemStatus.PENDING, results[1].status)
        verify(exactly = 1) { invoiceRequestItemRepository.save(any()) }
    }

    @Test
    fun `invoiceStatus returns the latest item for a delivery`() {
        val deliveryId = UUID.randomUUID()
        val invoiceId = UUID.randomUUID()
        every {
            invoiceRequestItemRepository.findFirstByDeliveryIdOrderByUpdatedAtDesc(deliveryId)
        } returns item(deliveryId, InvoiceItemStatus.SUCCEEDED, invoiceId)

        val result = service.invoiceStatus(deliveryId)

        assertEquals(InvoiceItemStatus.SUCCEEDED, result.status)
        assertEquals(invoiceId, result.invoiceId)
    }

    @Test
    fun `invoiceStatus throws when nothing was queued for the delivery`() {
        val deliveryId = UUID.randomUUID()
        every { invoiceRequestItemRepository.findFirstByDeliveryIdOrderByUpdatedAtDesc(deliveryId) } returns null

        assertThrows(InvoiceRequestNotFoundException::class.java) { service.invoiceStatus(deliveryId) }
    }
}
