package com.duri.settlement.dto

import com.duri.expense.dto.MemberRef
import com.duri.settlement.domain.SettlementStatus
import com.duri.user.domain.Bank
import java.time.Instant
import java.time.LocalDate

data class SettlementResponse(
    /** 아직 확정 전이라 정산 레코드가 없으면 null. */
    val settlementId: Long?,
    /** "2026-09" */
    val period: String,
    val from: LocalDate,
    val to: LocalDate,
    val status: SettlementStatus,
    val netAmount: Long,
    /** 받을 사람. 정산할 금액이 없으면 null. */
    val creditor: MemberRef?,
    /** 보낼 사람. 정산할 금액이 없으면 null. */
    val debtor: MemberRef?,
    val expenseCount: Long,
    val totalAmount: Long,
    /** 채권자가 계좌를 등록해 두었을 때만 채워진다. */
    val transfer: TransferGuide?,
    val confirmedAt: Instant?,
    val confirmedBy: MemberRef?,
)

/**
 * 송금 딥링크는 이번 범위 밖이라, 앱이 할 수 있는 최선은
 * "계좌와 금액을 한 번에 복사하게 해 주는 것"이다.
 */
data class TransferGuide(
    val bank: Bank,
    val bankName: String,
    val accountNo: String,
    val holderName: String,
    val amount: Long,
    /** 복사 버튼 한 번으로 메신저에 그대로 붙여넣을 한 줄. */
    val copyText: String,
)

data class SettlementHistoryResponse(
    val settlementId: Long,
    val period: String,
    val status: SettlementStatus,
    val netAmount: Long,
    val creditor: MemberRef?,
    val debtor: MemberRef?,
    val confirmedAt: Instant?,
)
