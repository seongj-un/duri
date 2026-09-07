package com.duri.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "duri.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
