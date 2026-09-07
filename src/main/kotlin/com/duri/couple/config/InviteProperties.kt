package com.duri.couple.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "duri.invite")
data class InviteProperties(
    val ttl: Duration,
    /** 초대 링크의 프론트엔드 주소. 최종 링크는 "{baseUrl}/{token}" 형태가 된다. */
    val baseUrl: String,
)
