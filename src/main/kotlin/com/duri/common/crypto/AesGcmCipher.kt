package com.duri.common.crypto

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 양방향 암호화.
 *
 * 저장 포맷은 base64(iv ‖ ciphertext‖tag) 하나로 합친다.
 * IV 는 매 호출마다 새로 뽑으므로 같은 평문도 매번 다른 암호문이 된다
 * (= 암호문으로는 검색할 수 없다. 계좌번호는 조회 조건이 아니라 표시용이므로 문제되지 않는다).
 */
@Component
class AesGcmCipher(properties: CryptoProperties) {

    private val key: SecretKeySpec = Base64.getDecoder().decode(properties.accountKey)
        .also {
            require(it.size == KEY_SIZE_BYTES) {
                "duri.crypto.account-key 는 base64 로 인코딩된 ${KEY_SIZE_BYTES}바이트여야 합니다. (현재 ${it.size}바이트)"
            }
        }
        .let { SecretKeySpec(it, "AES") }

    private val random = SecureRandom()

    fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + cipherText)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > IV_SIZE_BYTES) { "암호문 길이가 올바르지 않습니다." }
        val iv = bytes.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = bytes.copyOfRange(IV_SIZE_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        }
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BYTES = 32
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
