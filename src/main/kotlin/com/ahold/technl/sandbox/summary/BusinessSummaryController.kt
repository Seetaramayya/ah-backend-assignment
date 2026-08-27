package com.ahold.technl.sandbox.summary

import com.ahold.technl.sandbox.summary.dto.BusinessSummaryResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/deliveries")
class BusinessSummaryController(
    private val businessSummaryService: BusinessSummaryService,
) {
    @GetMapping("/business-summary")
    fun businessSummary(): BusinessSummaryResponse = businessSummaryService.getYesterdaySummary()
}
