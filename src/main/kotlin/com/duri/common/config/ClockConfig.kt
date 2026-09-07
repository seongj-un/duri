package com.duri.common.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 시간은 항상 주입받은 Clock 에서 읽는다.
 * 만료·정산 기간처럼 시간이 규칙인 로직을 테스트에서 고정할 수 있게 하기 위함.
 */
@Configuration
class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun clock(): Clock = Clock.systemUTC()
}
