package com.duri.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Clock

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfig {

    /** ClockConfig 가 @ConditionalOnMissingBean 이라 이 빈이 대신 쓰인다. */
    @Bean
    fun mutableClock(): Clock = MutableClock()
}
