package com.duri.common.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse.of(e.errorCode, request.requestURI, e.message)
        // 도메인 규칙 위반은 정상적인 흐름이므로 스택트레이스를 남기지 않는다
        log.info("business error: code={} path={} message={}", e.errorCode.code, request.requestURI, e.message)
        return ResponseEntity.status(e.errorCode.status).body(body)
    }

    /**
     * BusinessException 의 하위 타입이지만 Retry-After 헤더가 더 붙는다.
     * 스프링은 더 구체적인 핸들러를 고르므로 위의 handleBusiness 대신 여기로 온다.
     */
    @ExceptionHandler(RateLimitedException::class)
    fun handleRateLimited(e: RateLimitedException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.info("rate limited: path={} retryAfterSeconds={}", request.requestURI, e.retryAfter.toSeconds())
        return ResponseEntity.status(e.errorCode.status)
            .header(HttpHeaders.RETRY_AFTER, e.retryAfter.toSeconds().toString())
            .body(ErrorResponse.of(e.errorCode, request.requestURI, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        e: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val fieldErrors = e.bindingResult.fieldErrors.map {
            ErrorResponse.FieldError(it.field, it.defaultMessage ?: "올바르지 않은 값입니다.")
        }
        val body = ErrorResponse(
            code = ErrorCode.INVALID_REQUEST.code,
            message = ErrorCode.INVALID_REQUEST.message,
            path = request.requestURI,
            timestamp = Instant.now(),
            fieldErrors = fieldErrors,
        )
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status).body(body)
    }

    /**
     * 도메인 엔티티의 require 실패. 값이 잘못된 것이므로 400 이 맞다.
     *
     * 다만 DTO 검증에서 걸렸어야 할 값이 여기까지 왔다는 뜻이기도 해서
     * 스택트레이스를 남겨 검증이 빠진 곳을 찾을 수 있게 한다.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        e: IllegalArgumentException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("illegal argument reached the domain layer at {}", request.requestURI, e)
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status)
            .body(
                ErrorResponse.of(
                    ErrorCode.INVALID_REQUEST,
                    request.requestURI,
                    e.message ?: ErrorCode.INVALID_REQUEST.message,
                ),
            )
    }

    /** 정적 리소스 404 는 소음이므로 그대로 404 로 넘긴다. */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ErrorCode.NOT_FOUND.status)
            .body(ErrorResponse.of(ErrorCode.NOT_FOUND, request.requestURI))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("unexpected error at {}", request.requestURI, e)
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status)
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, request.requestURI))
    }
}
