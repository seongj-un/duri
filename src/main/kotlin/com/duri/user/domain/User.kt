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
    /** 로그인 아이디. 소문자로 정규화된 값만 들어온다. */
    @Column(name = "email", nullable = false, length = 255, updatable = false)
    val email: String,

    @Column(name = "password_hash", nullable = false, length = 60)
    var passwordHash: String,

    @Column(name = "nickname", nullable = false, length = 50)
    var nickname: String,

    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null,
) : BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    fun withdraw() {
        status = UserStatus.WITHDRAWN
    }

    companion object {
        fun register(email: String, passwordHash: String, nickname: String) = User(
            email = email,
            passwordHash = passwordHash,
            nickname = nickname.ifBlank { DEFAULT_NICKNAME },
        )

        const val DEFAULT_NICKNAME = "이름 없는 사용자"
    }
}
