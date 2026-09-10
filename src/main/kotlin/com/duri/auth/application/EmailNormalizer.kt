package com.duri.auth.application

/**
 * 이메일은 저장·조회·집계 어디서나 같은 형태여야 한다.
 *
 * 대소문자만 다른 이메일로 계정이 둘 생기면 사용자는 왜 로그인이 안 되는지 알 수 없고,
 * 레이트 리밋이 이 규칙을 따르지 않으면 대소문자만 바꿔 카운터를 빠져나갈 수 있다.
 */
object EmailNormalizer {

    /** Kotlin 의 lowercase() 는 로케일과 무관하다. 터키어 I 문제가 생기지 않는다. */
    fun normalize(email: String): String = email.trim().lowercase()
}
