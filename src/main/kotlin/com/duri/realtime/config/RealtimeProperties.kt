package com.duri.realtime.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "duri.realtime")
data class RealtimeProperties(
    /** 이 시간이 지나면 연결을 끊는다. 클라이언트는 자동으로 다시 붙는다. */
    val timeout: Duration = Duration.ofMinutes(30),
    val heartbeatMillis: Long = 25_000,
)
