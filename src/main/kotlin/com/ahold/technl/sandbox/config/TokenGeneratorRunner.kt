package com.ahold.technl.sandbox.config

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant

// Dev-only: mints a sample JWT since no real identity provider is wired up yet (see README).
@Component
@Profile("gen-token")
class TokenGeneratorRunner(
    private val jwtEncoder: JwtEncoder,
    private val context: ConfigurableApplicationContext,
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val claims =
            JwtClaimsSet
                .builder()
                .subject("local-dev-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        println("Sample JWT (valid until 2099-01-01):\n$token")
        SpringApplication.exit(context)
    }
}
