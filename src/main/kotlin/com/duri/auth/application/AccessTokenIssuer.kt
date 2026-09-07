package com.duri.auth.application

import com.duri.auth.config.JwtProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class AccessTokenIssuer(
    private val jwtEncoder: JwtEncoder,
    private val properties: JwtProperties,
    private val clock: Clock,
) {

    fun issue(userId: Long): IssuedAccessToken {
        val now = clock.instant()
        val expiresAt = now.plus(properties.accessTokenTtl)

        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
            .build()

        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

        return IssuedAccessToken(
            value = token,
            expiresAt = expiresAt,
            expiresInSeconds = properties.accessTokenTtl.seconds,
        )
    }

    companion object {
        const val CLAIM_TOKEN_TYPE = "typ"
        const val TOKEN_TYPE_ACCESS = "access"
    }
}

data class IssuedAccessToken(
    val value: String,
    val expiresAt: Instant,
    val expiresInSeconds: Long,
)
