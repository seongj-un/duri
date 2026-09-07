package com.duri.couple.dto

import com.duri.couple.domain.InviteStatus
import java.time.Instant

data class InviteResponse(
    /** 원문 토큰. 발급 응답에서 단 한 번만 내려간다. */
    val token: String,
    /** 상대에게 그대로 공유할 링크. */
    val inviteUrl: String,
    val expiresAt: Instant,
)

/**
 * 로그인 전에도 "누가 초대했는지"를 보여주기 위한 응답.
 * 토큰을 가진 사람만 볼 수 있고, 커플 내부 정보는 담지 않는다.
 */
data class InvitePreviewResponse(
    val coupleName: String,
    val inviterNickname: String,
    val status: InviteStatus,
    val expiresAt: Instant,
)

data class InviteAcceptResponse(
    val coupleId: Long,
)
