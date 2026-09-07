package com.duri.expense.domain

/** DB 의 ck_expenses_category 체크 제약과 1:1 로 대응한다. 추가 시 마이그레이션이 필요하다. */
enum class ExpenseCategory(val displayName: String) {
    RENT("월세"),
    UTILITY("공과금"),
    GROCERY("장보기"),
    DINING("외식"),
    DATE("데이트"),
    TRANSPORT("교통"),
    SHOPPING("쇼핑"),
    TRAVEL("여행"),
    SUBSCRIPTION("구독"),
    ETC("기타"),
}
