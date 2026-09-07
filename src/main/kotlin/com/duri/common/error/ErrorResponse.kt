package com.duri.common.error

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Instant,
    val fieldErrors: List<FieldError>? = null,
) {
    data class FieldError(
        val field: String,
        val reason: String,
    )

    companion object {
        fun of(errorCode: ErrorCode, path: String, message: String = errorCode.message) =
            ErrorResponse(errorCode.code, message, path, Instant.now())
    }
}
