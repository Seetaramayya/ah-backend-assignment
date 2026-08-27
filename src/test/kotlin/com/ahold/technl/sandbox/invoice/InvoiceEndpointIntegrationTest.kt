package com.ahold.technl.sandbox.invoice

import com.ahold.technl.sandbox.delivery.Delivery
import com.ahold.technl.sandbox.delivery.DeliveryStatus
import com.ahold.technl.sandbox.error.ErrorResponse
import com.ahold.technl.sandbox.invoice.dto.InvoiceBatchRequest
import com.ahold.technl.sandbox.invoice.dto.InvoiceItemResult
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import com.ahold.technl.sandbox.support.AbstractIntegrationTest
import java.time.Instant
import java.util.UUID

class InvoiceEndpointIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var invoicePoller: InvoicePoller

    @Value("\${app.invoice.max-delivery-ids}")
    private var maxDeliveryIds: Int = 0

    @BeforeEach
    fun resetWireMock() {
        wireMockServer.resetRequests()
        wireMockServer.resetToDefaultMappings()
    }

    private fun aDelivery(): Delivery =
        deliveryRepository.save(
            Delivery(
                id = UUID.randomUUID(),
                vehicleId = "AHV-589",
                address = "Example street 15A",
                startedAt = Instant.parse("2023-10-09T12:45:34.678Z"),
                finishedAt = null,
                status = DeliveryStatus.IN_PROGRESS,
            ),
        )

    @Test
    fun `queues a known delivery as pending, then the poller invoices it`() {
        val delivery = aDelivery()

        val accepted = invoice(listOf(delivery.id))
        assertEquals(HttpStatus.ACCEPTED, accepted.statusCode)
        assertEquals(InvoiceItemStatus.PENDING, accepted.body!![0].status)
        assertNull(accepted.body!![0].invoiceId)

        invoicePoller.drainPending()

        val status = invoiceStatus(delivery.id, InvoiceItemResult::class.java)
        assertEquals(HttpStatus.OK, status.statusCode)
        assertEquals(InvoiceItemStatus.SUCCEEDED, status.body!!.status)
        assertNotNull(status.body!!.invoiceId)
        assertEquals(1, wireMockServer.findAll(postRequestedFor(urlEqualTo("/v1/invoices"))).size)
    }

    @Test
    fun `an already invoiced delivery is resolved immediately on a repeat POST`() {
        val delivery = aDelivery()
        invoice(listOf(delivery.id))
        invoicePoller.drainPending()

        val repeat = invoice(listOf(delivery.id))

        assertEquals(HttpStatus.ACCEPTED, repeat.statusCode)
        assertEquals(InvoiceItemStatus.SUCCEEDED, repeat.body!![0].status)
        assertNotNull(repeat.body!![0].invoiceId)
        assertEquals(1, wireMockServer.findAll(postRequestedFor(urlEqualTo("/v1/invoices"))).size)
    }

    @Test
    fun `a repeat POST before the poller runs does not queue a second item`() {
        val delivery = aDelivery()

        val first = invoice(listOf(delivery.id))
        val second = invoice(listOf(delivery.id))

        assertEquals(InvoiceItemStatus.PENDING, first.body!![0].status)
        assertEquals(InvoiceItemStatus.PENDING, second.body!![0].status)
        assertEquals(1, invoiceRequestItemRepository.count())

        invoicePoller.drainPending()
        assertEquals(1, wireMockServer.findAll(postRequestedFor(urlEqualTo("/v1/invoices"))).size)
    }

    @Test
    fun `unknown delivery id comes back failed in the accepted response`() {
        val response = invoice(listOf(UUID.randomUUID()))

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals(InvoiceItemStatus.FAILED, response.body!![0].status)
        assertNotNull(response.body!![0].error)
    }

    @Test
    fun `poller marks the item failed when the invoice service keeps erroring`() {
        val delivery = aDelivery()
        invoice(listOf(delivery.id))
        wireMockServer.stubFor(
            post(urlPathEqualTo("/v1/invoices")).atPriority(1).willReturn(aResponse().withStatus(500)),
        )

        invoicePoller.drainPending()

        val status = invoiceStatus(delivery.id, InvoiceItemResult::class.java)
        assertEquals(InvoiceItemStatus.FAILED, status.body!!.status)
        assertNotNull(status.body!!.error)
    }

    @Test
    fun `invoice status is 404 for a delivery that was never queued`() {
        val response = invoiceStatus(UUID.randomUUID(), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("INVOICE_REQUEST_NOT_FOUND", response.body?.code)
    }

    @Test
    fun `rejects a batch larger than the limit`() {
        val ids = (0..maxDeliveryIds).map { UUID.randomUUID() }

        val response =
            restTemplate.exchange(
                "/deliveries/invoice",
                HttpMethod.POST,
                HttpEntity(InvoiceBatchRequest(ids), authHeaders()),
                ErrorResponse::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.code)
    }

    private fun invoice(deliveryIds: List<UUID>) =
        restTemplate.exchange(
            "/deliveries/invoice",
            HttpMethod.POST,
            HttpEntity(InvoiceBatchRequest(deliveryIds), authHeaders()),
            object : ParameterizedTypeReference<List<InvoiceItemResult>>() {},
        )

    private fun <T> invoiceStatus(
        deliveryId: UUID,
        type: Class<T>,
    ) = restTemplate.exchange(
        "/deliveries/$deliveryId/invoice",
        HttpMethod.GET,
        HttpEntity<Void>(authHeaders()),
        type,
    )
}
