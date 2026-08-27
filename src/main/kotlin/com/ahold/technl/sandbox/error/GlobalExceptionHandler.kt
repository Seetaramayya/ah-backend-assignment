package com.ahold.technl.sandbox.error

import com.ahold.technl.sandbox.delivery.DeliveryConflictException
import com.ahold.technl.sandbox.delivery.DeliveryNotFoundException
import com.ahold.technl.sandbox.delivery.IllegalDeliveryStateException
import com.ahold.technl.sandbox.delivery.InvalidDeliveryTimeException
import com.ahold.technl.sandbox.invoice.InvoiceRequestNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DeliveryNotFoundException::class)
    fun handleDeliveryNotFound(ex: DeliveryNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(code = "DELIVERY_NOT_FOUND", message = ex.message ?: "Delivery not found"))

    @ExceptionHandler(IllegalDeliveryStateException::class)
    fun handleIllegalDeliveryState(ex: IllegalDeliveryStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(code = "ILLEGAL_DELIVERY_STATE", message = ex.message ?: "Delivery cannot be completed"))

    @ExceptionHandler(DeliveryConflictException::class)
    fun handleDeliveryConflict(ex: DeliveryConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(code = "DELIVERY_CONFLICT", message = ex.message ?: "Conflicting delivery already exists"))

    @ExceptionHandler(InvoiceRequestNotFoundException::class)
    fun handleInvoiceRequestNotFound(ex: InvoiceRequestNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(code = "INVOICE_REQUEST_NOT_FOUND", message = ex.message ?: "No invoice request found"))

    @ExceptionHandler(InvalidDeliveryTimeException::class)
    fun handleInvalidDeliveryTime(ex: InvalidDeliveryTimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_DELIVERY_TIME", message = ex.message ?: "finishedAt must be after startedAt"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldDetails = ex.bindingResult.fieldErrors.map { ErrorDetail(it.field, it.defaultMessage ?: "invalid value") }
        val globalDetails = ex.bindingResult.globalErrors.map { ErrorDetail(null, it.defaultMessage ?: "invalid value") }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    code = "VALIDATION_ERROR",
                    message = "Request validation failed",
                    details = fieldDetails + globalDetails,
                ),
            )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "MALFORMED_REQUEST", message = "Request body is malformed or unreadable"))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(code = "INTERNAL_ERROR", message = "An unexpected error occurred"))
    }
}
