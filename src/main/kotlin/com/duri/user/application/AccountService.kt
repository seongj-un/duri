package com.duri.user.application

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.user.domain.Account
import com.duri.user.domain.AccountRepository
import com.duri.user.domain.Bank
import com.duri.user.dto.AccountRequest
import com.duri.user.dto.AccountResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountService(
    private val accountRepository: AccountRepository,
) {

    /** 사용자당 계좌는 하나다. 있으면 갈아끼우고 없으면 만든다. */
    @Transactional
    fun upsert(userId: Long, request: AccountRequest): AccountResponse {
        val bank = requireNotNull(request.bank)
        val accountNo = requireNotNull(request.accountNo).replace("-", "")
        val holderName = requireNotNull(request.holderName).trim()

        val account = accountRepository.findByUserId(userId)
            ?.apply { update(bank.code, bank.displayName, accountNo, holderName) }
            ?: accountRepository.save(
                Account(
                    userId = userId,
                    bankCode = bank.code,
                    bankName = bank.displayName,
                    accountNo = accountNo,
                    holderName = holderName,
                ),
            )

        return AccountResponse.of(account, bank)
    }

    @Transactional(readOnly = true)
    fun get(userId: Long): AccountResponse {
        val account = accountRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.ACCOUNT_NOT_REGISTERED)
        return AccountResponse.of(account, account.bank())
    }

    @Transactional
    fun delete(userId: Long) {
        accountRepository.findByUserId(userId)?.let(accountRepository::delete)
    }
}

/**
 * 저장된 표준 코드를 열거형으로 되돌린다.
 * 코드 목록에서 은행이 빠지는 마이그레이션이 있었다면 여기서 드러난다.
 */
internal fun Account.bank(): Bank =
    Bank.ofCode(bankCode) ?: throw BusinessException(
        ErrorCode.UNSUPPORTED_BANK,
        "등록된 은행 코드($bankCode)를 더 이상 지원하지 않습니다. 계좌를 다시 등록해 주세요.",
    )
