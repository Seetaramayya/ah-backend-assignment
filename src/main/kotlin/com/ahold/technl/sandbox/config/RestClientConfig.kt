package com.ahold.technl.sandbox.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    @Value("\${invoice-service.url}") private val invoiceServiceUrl: String,
) {
    @Bean
    fun invoiceServiceRestClient(builder: RestClient.Builder): RestClient = builder.baseUrl(invoiceServiceUrl).build()
}
