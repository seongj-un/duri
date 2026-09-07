package com.duri.common.error

/**
 * 도메인 규칙 위반. GlobalExceptionHandler 가 ErrorCode 의 상태코드로 변환한다.
 */
class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
