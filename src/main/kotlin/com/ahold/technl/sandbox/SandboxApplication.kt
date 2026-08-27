package com.ahold.technl.sandbox

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SandboxApplication

fun main(args: Array<String>) {
    runApplication<SandboxApplication>(*args)
}
