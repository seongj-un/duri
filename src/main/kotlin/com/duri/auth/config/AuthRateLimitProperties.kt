package com.duri.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 로그인·회원가입 무차별 대입 방어의 한도.
 *
 * 기본값은 application.yml 에 적어 두었고, 여기 값은 프로퍼티가 없을 때의 안전망이다.
 */
@ConfigurationProperties(prefix = "duri.auth.rate-limit")
data class AuthRateLimitProperties(
    /** 고정 창(fixed window). 창이 지나면 카운터가 처음부터 다시 센다. */
    val window: Duration = Duration.ofMinutes(10),
    /** 분산된 IP 에서 한 계정을 노리는 것을 막는다. */
    val maxAttemptsPerEmail: Int = 10,
    /** 한 회선에서 여러 계정을 돌려 시도하는 것을 막는다. */
    val maxAttemptsPerIp: Int = 30,
) {
    init {
        require(!window.isZero && !window.isNegative) { "레이트 리밋 창은 양수여야 합니다." }
        require(maxAttemptsPerEmail > 0) { "이메일당 한도는 1 이상이어야 합니다." }
        require(maxAttemptsPerIp > 0) { "IP 당 한도는 1 이상이어야 합니다." }
    }
}
