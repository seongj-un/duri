package com.duri.common.error

import org.springframework.http.HttpStatus

/**
 * 클라이언트가 분기할 수 있도록 code 는 안정적으로 유지한다.
 * message 는 사용자에게 그대로 보여줄 수 있는 문장으로 쓴다.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),

    // 인증
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 제공자입니다."),
    OAUTH_PROFILE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "소셜 계정 정보를 가져오지 못했습니다."),
    REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해 주세요."),

    // 커플 space
    COUPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "커플 space를 찾을 수 없습니다."),
    ALREADY_IN_COUPLE(HttpStatus.CONFLICT, "이미 참여 중인 커플 space가 있습니다."),
    NOT_COUPLE_MEMBER(HttpStatus.FORBIDDEN, "이 커플 space의 구성원이 아닙니다."),
    COUPLE_ALREADY_FULL(HttpStatus.CONFLICT, "이미 두 사람이 연결된 커플 space입니다."),
    COUPLE_NOT_ACTIVE(HttpStatus.CONFLICT, "아직 파트너와 연결되지 않았습니다."),

    // 초대
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 링크를 찾을 수 없습니다."),
    INVITE_EXPIRED(HttpStatus.GONE, "만료된 초대 링크입니다."),
    INVITE_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 초대 링크입니다."),
    INVITE_REVOKED(HttpStatus.GONE, "취소된 초대 링크입니다."),
    CANNOT_INVITE_SELF(HttpStatus.BAD_REQUEST, "본인이 만든 초대는 수락할 수 없습니다."),

    // 지출
    EXPENSE_NOT_FOUND(HttpStatus.NOT_FOUND, "지출 내역을 찾을 수 없습니다."),
    EXPENSE_LOCKED(HttpStatus.CONFLICT, "이미 정산이 확정된 지출은 수정하거나 삭제할 수 없습니다."),
    PAYER_NOT_IN_COUPLE(HttpStatus.BAD_REQUEST, "결제자는 두 사람 중 한 명이어야 합니다."),
    PERIOD_ALREADY_SETTLED(HttpStatus.CONFLICT, "이미 정산이 확정된 달입니다."),

    // 정산 · 계좌
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산 내역을 찾을 수 없습니다."),
    FUTURE_PERIOD_NOT_SETTLEABLE(HttpStatus.BAD_REQUEST, "아직 오지 않은 달은 정산할 수 없습니다."),
    ACCOUNT_NOT_REGISTERED(HttpStatus.NOT_FOUND, "등록된 계좌가 없습니다."),
    UNSUPPORTED_BANK(HttpStatus.BAD_REQUEST, "지원하지 않는 은행입니다."),
    ;

    /** 열거 상수 이름을 그대로 응답 code 로 쓴다. */
    val code: String get() = name
}
