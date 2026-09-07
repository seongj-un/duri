package com.duri.realtime.domain

import java.time.Instant

/**
 * 커플 space 안에서 일어난 변화. 상대 화면을 다시 그리게 하는 신호다.
 *
 * 데이터 자체는 싣지 않고 "무엇이 어느 달에서 바뀌었는지"만 알린다.
 * 페이로드를 실으면 권한 검사를 이벤트 발행 지점마다 다시 해야 하고,
 * 받는 쪽이 이미 갖고 있는 화면 상태와 어긋날 수 있기 때문이다.
 */
data class CoupleEvent(
    val type: CoupleEventType,
    val coupleId: Long,
    /** 이 변화를 일으킨 사람. 본인 화면은 이미 갱신됐으므로 무시할 수 있다. */
    val actorId: Long?,
    val resourceId: Long?,
    /** 영향받은 달("2026-09"). 프론트가 그 달만 다시 불러오면 된다. */
    val period: String?,
    val occurredAt: Instant,
)

enum class CoupleEventType {
    EXPENSE_CREATED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    SETTLEMENT_CONFIRMED,
    RECURRING_EXPENSE_GENERATED,
    NOTIFICATION_CREATED,
}
