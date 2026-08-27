package com.ahold.technl.sandbox.config

import com.ahold.technl.sandbox.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * Default (non-docker) profile: only health/info are public; prometheus needs auth
 * (`app.observability.prometheus-scrape-open` defaults to false).
 */
class ActuatorAccessIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `health is public`() {
        assertEquals(HttpStatus.OK, restTemplate.getForEntity("/actuator/health", String::class.java).statusCode)
    }

    @Test
    fun `prometheus requires authentication by default`() {
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            restTemplate.getForEntity("/actuator/prometheus", String::class.java).statusCode,
        )
    }
}
