package com.duri.settlement.domain

enum class SettlementStatus {
    /** 아직 확정 전. 지출이 추가·수정되면 순잔액이 계속 바뀐다. */
    OPEN,

    /** 확정 완료. 귀속된 지출은 잠기고 금액이 고정된다. */
    CONFIRMED,
}
