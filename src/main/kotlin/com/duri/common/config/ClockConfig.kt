package com.duri.common.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * 시간은 항상 주입받은 Clock 에서 읽는다.
 * 만료·정산 기간처럼 시간이 규칙인 로직을 테스트에서 고정할 수 있게 하기 위함.
 *
 * 시간대는 한국이다. "이번 달", "오늘 발생하는 반복지출" 같은 판단이
 * 사용자가 보는 달력과 어긋나면 안 되기 때문이다.
 * (Instant 는 절대 시각이라 시간대와 무관하게 그대로다.)
 */
@Configuration
class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun clock(): Clock = Clock.system(KST)

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
