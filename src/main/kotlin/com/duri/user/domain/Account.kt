package com.duri.user.domain

import com.duri.common.crypto.EncryptedStringConverter
import com.duri.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 정산 화면에 "이 계좌로 보내주세요"를 띄우기 위한 입금 계좌. 사용자당 1개.
 * 송금 딥링크는 이번 범위 밖이라, 계좌번호는 표시·복사 용도로만 쓴다.
 */
@Entity
@Table(name = "accounts")
class Account(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "bank_code", nullable = false, length = 10)
    var bankCode: String,

    @Column(name = "bank_name", nullable = false, length = 50)
    var bankName: String,

    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "account_no", nullable = false, length = 128)
    var accountNo: String,

    @Column(name = "holder_name", nullable = false, length = 50)
    var holderName: String,
) : BaseTimeEntity() {

    fun update(bankCode: String, bankName: String, accountNo: String, holderName: String) {
        this.bankCode = bankCode
        this.bankName = bankName
        this.accountNo = accountNo
        this.holderName = holderName
    }

    /** 목록·미리보기용 마스킹. 뒤 4자리만 남긴다. */
    fun maskedAccountNo(): String {
        val digits = accountNo.filter(Char::isDigit)
        if (digits.length <= VISIBLE_TAIL) return "*".repeat(digits.length)
        return "*".repeat(digits.length - VISIBLE_TAIL) + digits.takeLast(VISIBLE_TAIL)
    }

    private companion object {
        const val VISIBLE_TAIL = 4
    }
}
