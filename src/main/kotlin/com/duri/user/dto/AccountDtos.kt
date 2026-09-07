package com.duri.user.dto

import com.duri.user.domain.Account
import com.duri.user.domain.Bank
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AccountRequest(
    @field:NotNull(message = "은행을 선택해 주세요.")
    val bank: Bank?,

    @field:NotBlank(message = "계좌번호를 입력해 주세요.")
    @field:Pattern(
        regexp = "^[0-9-]{8,30}$",
        message = "계좌번호는 숫자와 하이픈만 사용해 8자 이상 입력해 주세요.",
    )
    val accountNo: String?,

    @field:NotBlank(message = "예금주를 입력해 주세요.")
    @field:Size(max = 50, message = "예금주는 50자 이하로 입력해 주세요.")
    val holderName: String?,
)

data class AccountResponse(
    val bank: Bank,
    val bankName: String,
    val accountNo: String,
    val holderName: String,
) {
    companion object {
        /** 본인이 자기 계좌를 볼 때. 수정 화면에 그대로 채워야 하므로 전체를 준다. */
        fun of(account: Account, bank: Bank) = AccountResponse(
            bank = bank,
            bankName = account.bankName,
            accountNo = account.accountNo,
            holderName = account.holderName,
        )
    }
}
