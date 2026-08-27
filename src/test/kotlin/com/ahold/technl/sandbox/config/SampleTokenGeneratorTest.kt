package com.ahold.technl.sandbox.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant
import java.time.temporal.ChronoUnit

// Not an app component: run `./mvnw test -Dtest=SampleTokenGeneratorTest` and read the token from
// stdout when you need a fresh one for manual testing
class SampleTokenGeneratorTest {
    private val defaultDummySecret = "local-dev-secret-DO-NOT-USE-IN-PRODUCTION-please-override-me"
    @Test
    fun `app's own encoder and decoder round-trip a generated token`() {
        val secret = System.getenv("APP_SECURITY_JWT_SECRET") ?: defaultDummySecret

        val securityConfig = SecurityConfig(jwtSecret = secret, prometheusScrapeOpen = false)
        val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val oneDay = 60 * 60 * 24L
        val expiryDate = now.plusSeconds(oneDay * 30)

        val claims =
            JwtClaimsSet
                .builder()
                .subject("local-dev-user")
                .issuedAt(now)
                .expiresAt(expiryDate)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = securityConfig.jwtEncoder().encode(JwtEncoderParameters.from(header, claims)).tokenValue

        val decoded = securityConfig.jwtDecoder().decode(token)
        assertEquals("local-dev-user", decoded.subject)

        println("*".repeat(150))
        println("Sample JWT (generated at $now valid until $expiryDate):")
        println("secret=$secret\n")
        println("token=$token\n")
        println("*".repeat(150))
    }
}
