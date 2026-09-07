package com.duri.common.crypto

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "duri.crypto")
data class CryptoProperties(
    /** AES-256 키. base64 로 인코딩된 32바이트. */
    val accountKey: String,
)
