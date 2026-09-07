package com.duri.auth.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 액세스 토큰은 대칭키(HS256) JWT 다. 발급자와 검증자가 같은 서버 하나뿐이라
 * 비대칭키의 이점(공개키 배포)이 없고 운영 부담만 늘기 때문이다.
 */
@Configuration
class JwtConfig {

    @Bean
    fun jwtSecretKey(properties: JwtProperties): SecretKey {
        val decoded = Base64.getDecoder().decode(properties.secret)
        require(decoded.size >= MIN_KEY_SIZE_BYTES) {
            "duri.jwt.secret 은 base64 로 인코딩된 ${MIN_KEY_SIZE_BYTES}바이트 이상이어야 합니다. (현재 ${decoded.size}바이트)"
        }
        return SecretKeySpec(decoded, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(jwtSecretKey: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(jwtSecretKey))

    /**
     * 만료 검증은 발급할 때와 같은 Clock 을 쓴다.
     * 그래야 테스트에서 시간을 앞으로 돌려 "토큰이 실제로 만료되는지"를 검증할 수 있다.
     */
    @Bean
    fun jwtDecoder(jwtSecretKey: SecretKey, properties: JwtProperties, clock: Clock): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        JwtTimestampValidator().apply { setClock(clock) },
                        JwtIssuerValidator(properties.issuer),
                    ),
                )
            }

    private companion object {
        const val MIN_KEY_SIZE_BYTES = 32
    }
}
