package com.duri.auth.application

import com.duri.auth.config.AuthRateLimitProperties
import com.duri.common.error.RateLimitedException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 로그인·회원가입 시도를 IP 와 이메일 두 축으로 세어 무차별 대입을 막는다.
 *
 * 인메모리 고정 창(fixed window) 이다. Redis 나 bucket4j 를 들이지 않은 이유:
 * 이 앱은 단일 인스턴스로 돌고 사용자가 두 명이다. 카운터를 밖에 둘 이유가 없고,
 * 재시작하면 카운터가 사라지지만 그건 공격자가 서버를 재시작시킬 수 있을 때의 이야기다.
 *
 * 두 축을 쓰는 이유:
 * - IP: 한 회선에서 여러 계정을 돌려 시도하는 것을 막는다
 * - 이메일: 분산된 IP 에서 한 계정을 노리는 것을 막는다
 */
@Component
class AuthRateLimiter(
    private val properties: AuthRateLimitProperties,
    private val clock: Clock,
) {

    private val windows = ConcurrentHashMap<String, Attempts>()
    private val lastSweptAt = AtomicReference(Instant.EPOCH)

    /**
     * 시도 한 번을 센다. 한도를 넘겼으면 [RateLimitedException] 을 던진다.
     *
     * 사용자가 존재하는지 보지 않는다. DB 를 건드리기 전에 세어야
     * "제한에 걸리는 방식이 다르다" 는 것만으로 가입 여부가 새어나가지 않는다.
     */
    fun countAttempt(clientIp: String, email: String) {
        val now = clock.instant()
        sweepExpired(now)

        // 두 축을 모두 센 뒤에 판단한다. IP 가 이미 막혔다는 이유로 이메일 축을 세지 않으면
        // IP 를 갈아타는 공격에서 이메일 카운터가 헐거워진다.
        val byIp = hit(ipKey(clientIp), properties.maxAttemptsPerIp, now)
        val byEmail = hit(emailKey(email), properties.maxAttemptsPerEmail, now)

        val retryAfter = listOfNotNull(byIp, byEmail).maxOrNull() ?: return
        throw RateLimitedException(retryAfter)
    }

    /**
     * 정상 사용자로 확인됐으니 두 축의 카운터를 비운다.
     *
     * 비밀번호를 두어 번 틀렸다가 맞힌 사람이 곧바로 잠기면 안 된다.
     * 성공한 로그인에만 쓴다. 가입 성공에 쓰면 아무 이메일로나 가입해서
     * IP 카운터를 지울 수 있는 우회로가 생긴다.
     */
    fun clearAttempts(clientIp: String, email: String) {
        windows.remove(ipKey(clientIp))
        windows.remove(emailKey(email))
    }

    /** 테스트 격리용. 운영 경로에서는 호출하지 않는다. */
    fun clearAll() {
        windows.clear()
        lastSweptAt.set(Instant.EPOCH)
    }

    /** 한 번 세고, 한도를 넘었으면 얼마나 기다려야 하는지 돌려준다. 한도 안이면 null. */
    private fun hit(key: String, maxAttempts: Int, now: Instant): Duration? {
        val attempts = requireNotNull(
            windows.compute(key) { _, current ->
                if (current == null || current.isExpired(now, properties.window)) {
                    Attempts(now, 1)
                } else {
                    Attempts(current.startedAt, current.count + 1)
                }
            },
        )

        if (attempts.count <= maxAttempts) return null
        return retryAfter(attempts.startedAt, now)
    }

    /** 남은 시간을 초 단위로 올림한다. 0초를 주면 클라이언트가 곧바로 다시 와서 또 막힌다. */
    private fun retryAfter(startedAt: Instant, now: Instant): Duration {
        val remainingMillis = Duration.between(now, startedAt.plus(properties.window)).toMillis()
        return Duration.ofSeconds(maxOf(1L, (remainingMillis + 999) / 1000))
    }

    /**
     * 만료된 항목을 치운다. 이게 없으면 공격자가 이메일을 바꿔가며 맵을 무한히 키울 수 있다.
     *
     * 요청마다 전체를 훑는 것은 낭비라 창 하나에 한 번만 돈다.
     * 창이 지나기 전의 항목은 어차피 살아 있어야 하므로 이 주기로 충분하다.
     */
    private fun sweepExpired(now: Instant) {
        val last = lastSweptAt.get()
        if (Duration.between(last, now) < properties.window) return
        // 진 쪽은 그냥 넘어간다. 다른 스레드가 지금 치우고 있다는 뜻이다.
        if (!lastSweptAt.compareAndSet(last, now)) return

        windows.entries.removeIf { it.value.isExpired(now, properties.window) }
    }

    private fun ipKey(clientIp: String) = "ip:$clientIp"

    /** LocalAuthService 와 같은 정규화를 거쳐야 대소문자를 바꿔 카운터를 빠져나갈 수 없다. */
    private fun emailKey(email: String) = "email:${EmailNormalizer.normalize(email)}"

    private class Attempts(val startedAt: Instant, val count: Int) {
        fun isExpired(now: Instant, window: Duration) = !now.isBefore(startedAt.plus(window))
    }
}
