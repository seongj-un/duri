package com.duri.couple.domain

enum class CoupleStatus {
    /** 만든 사람 혼자. 파트너 초대를 기다리는 상태. */
    PENDING,

    /** 두 사람이 연결되어 지출 기록·정산이 가능한 상태. */
    ACTIVE,

    /** 관계 해제. 기록은 남기되 새 지출은 받지 않는다. */
    DISBANDED,
}
