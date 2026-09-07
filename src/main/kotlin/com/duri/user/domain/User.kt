package com.duri.user.domain

import com.duri.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    val provider: AuthProvider,

    @Column(name = "provider_id", nullable = false, length = 255, updatable = false)
    val providerId: String,

    @Column(name = "nickname", nullable = false, length = 50)
    var nickname: String,

    @Column(name = "email", length = 255)
    var email: String? = null,

    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null,
) : BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    /**
     * 로그인할 때마다 소셜 프로필을 최신으로 맞춘다.
     * 카카오는 동의 항목에 따라 값이 빠질 수 있으므로 null 은 "변경 없음"으로 취급한다.
     */
    fun syncProfile(nickname: String?, profileImageUrl: String?, email: String?) {
        nickname?.takeIf { it.isNotBlank() }?.let { this.nickname = it }
        profileImageUrl?.let { this.profileImageUrl = it }
        email?.let { this.email = it }
    }

    fun withdraw() {
        status = UserStatus.WITHDRAWN
    }

    companion object {
        /** 소셜 최초 로그인 시점의 사용자 생성. */
        fun register(
            provider: AuthProvider,
            providerId: String,
            nickname: String,
            email: String?,
            profileImageUrl: String?,
        ) = User(
            provider = provider,
            providerId = providerId,
            nickname = nickname.ifBlank { DEFAULT_NICKNAME },
            email = email,
            profileImageUrl = profileImageUrl,
        )

        const val DEFAULT_NICKNAME = "이름 없는 사용자"
    }
}
