package com.duri.user.domain

/**
 * 정산 계좌의 은행. 코드는 금융결제원 표준 기관코드다.
 *
 * 자유 문자열 대신 열거형으로 받는 이유: 계좌번호를 복사해 붙여넣는 화면에서
 * "국민"과 "KB국민"이 섞이면 사용자가 매번 확인해야 한다.
 */
enum class Bank(val code: String, val displayName: String) {
    KB("004", "국민"),
    SHINHAN("088", "신한"),
    WOORI("020", "우리"),
    HANA("081", "하나"),
    NH("011", "농협"),
    IBK("003", "기업"),
    SC("023", "SC제일"),
    CITI("027", "씨티"),
    KDB("002", "산업"),
    SUHYUP("007", "수협"),
    DGB("031", "대구"),
    BUSAN("032", "부산"),
    KYONGNAM("039", "경남"),
    KWANGJU("034", "광주"),
    JEONBUK("037", "전북"),
    JEJU("035", "제주"),
    POST("071", "우체국"),
    SAEMAUL("045", "새마을금고"),
    SHINHYUP("048", "신협"),
    KAKAOBANK("090", "카카오뱅크"),
    KBANK("089", "케이뱅크"),
    TOSSBANK("092", "토스뱅크"),
    ;

    companion object {
        private val BY_CODE = entries.associateBy { it.code }

        /** DB 에는 표준 코드를 저장한다. 앱 없이 테이블만 봐도 읽히도록. */
        fun ofCode(code: String): Bank? = BY_CODE[code]
    }
}
