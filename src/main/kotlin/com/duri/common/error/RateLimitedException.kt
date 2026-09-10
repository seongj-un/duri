package com.duri.common.error

import java.time.Duration

/**
 * 레이트 리밋에 걸렸다.
 *
 * BusinessException 을 그대로 쓰지 않는 이유는 Retry-After 때문이다.
 * "언제 다시 시도할 수 있는가" 는 에러 코드에 담을 수 없는, 요청마다 다른 값이다.
 */
class RateLimitedException(
    val retryAfter: Duration,
    errorCode: ErrorCode = ErrorCode.TOO_MANY_AUTH_ATTEMPTS,
) : BusinessException(errorCode)
