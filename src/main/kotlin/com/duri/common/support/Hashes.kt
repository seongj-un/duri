package com.duri.common.support

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/**
 * 초대 토큰 / 리프레시 토큰처럼 "원문은 클라이언트만, 서버는 대조만" 하는 값에 쓴다.
 *
 * 토큰은 128비트 난수라 사전 공격 대상이 아니므로 bcrypt 가 아닌 SHA-256 을 쓴다.
 * 대신 조회 키로 써야 하므로 결정적(salt 없음)이어야 한다.
 */
object Hashes {

    private val random = SecureRandom()
    private val hex = HexFormat.of()

    /** URL 에 그대로 넣을 수 있는 난수 토큰. */
    fun randomToken(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 64자 hex. DB 의 VARCHAR(64) 와 맞춘다. */
    fun sha256(value: String): String =
        hex.formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
}
