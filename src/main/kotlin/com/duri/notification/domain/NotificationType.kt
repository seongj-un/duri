package com.duri.notification.domain

/** DB 의 ck_notifications_type 체크 제약과 1:1 로 대응한다. 추가 시 마이그레이션이 필요하다. */
enum class NotificationType {
    /** 정산 기준일이 되었는데 지난달 정산이 아직 확정되지 않았을 때. */
    SETTLEMENT_REMINDER,
}
