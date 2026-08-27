package com.ahold.technl.sandbox.support

import com.ahold.technl.sandbox.delivery.DeliveryRepository
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jwtEncoder: JwtEncoder

    @Autowired
    lateinit var deliveryRepository: DeliveryRepository

    // The deliveries table now has a UNIQUE (vehicle_id, started_at) constraint, so rows left by a
    // prior test in this shared context would collide with fixed-payload inserts. Start each test clean.
    @BeforeEach
    fun clearDeliveries() {
        deliveryRepository.deleteAll()
    }

    fun authHeaders(): HttpHeaders = HttpHeaders().apply { setBearerAuth(issueToken()) }

    private fun issueToken(): String {
        val claims =
            JwtClaimsSet
                .builder()
                .subject("integration-test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    companion object {
        // Started once and never stopped for the whole test JVM (Testcontainers' Ryuk reaper
        // cleans up on exit) so that Spring's cached ApplicationContext keeps working across
        // test classes instead of pointing at a container another class's lifecycle has stopped.
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply { start() }

        val wireMockServer: WireMockServer =
            WireMockServer(WireMockConfiguration.options().dynamicPort().usingFilesUnderDirectory("wiremock")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("invoice-service.url") { "http://localhost:${wireMockServer.port()}" }
        }
    }
}
