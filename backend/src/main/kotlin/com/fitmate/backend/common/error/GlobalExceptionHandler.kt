package com.fitmate.backend.common.error

import com.fitmate.backend.common.api.ApiErrorBody
import com.fitmate.backend.common.api.ApiErrorField
import com.fitmate.backend.common.api.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.slf4j.LoggerFactory
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(exception.errorCode.status)
            .body(
                ErrorResponse(
                    error = ApiErrorBody(
                        code = exception.errorCode.name,
                        message = exception.message,
                    ),
                ),
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fields = exception.bindingResult
            .allErrors
            .mapNotNull { error ->
                when (error) {
                    is FieldError -> ApiErrorField(error.field, error.code ?: "INVALID")
                    else -> null
                }
            }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = ApiErrorBody(
                    code = ApiErrorCode.VALIDATION_ERROR.name,
                    message = ApiErrorCode.VALIDATION_ERROR.message,
                    fields = fields,
                ),
            ),
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val fields = exception.constraintViolations.map {
            ApiErrorField(
                field = it.propertyPath.toString().substringAfterLast('.'),
                reason = it.message,
            )
        }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = ApiErrorBody(
                    code = ApiErrorCode.VALIDATION_ERROR.name,
                    message = ApiErrorCode.VALIDATION_ERROR.message,
                    fields = fields,
                ),
            ),
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, HttpMessageNotReadableException::class)
    fun handleBadRequest(exception: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = ApiErrorBody(
                    code = ApiErrorCode.VALIDATION_ERROR.name,
                    message = ApiErrorCode.VALIDATION_ERROR.message,
                ),
            ),
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        logger.warn("Data integrity violation", exception)
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = ApiErrorBody(
                    code = ApiErrorCode.VALIDATION_ERROR.name,
                    message = ApiErrorCode.VALIDATION_ERROR.message,
                ),
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled API exception", exception)
        return ResponseEntity.internalServerError().body(
            ErrorResponse(
                error = ApiErrorBody(
                    code = ApiErrorCode.INTERNAL_SERVER_ERROR.name,
                    message = ApiErrorCode.INTERNAL_SERVER_ERROR.message,
                ),
            ),
        )
    }
}
