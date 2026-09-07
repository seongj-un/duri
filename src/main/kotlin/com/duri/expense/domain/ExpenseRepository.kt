package com.duri.expense.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ExpenseRepository : JpaRepository<Expense, Long> {

    /** 남의 커플 지출에 손대지 못하도록 커플 id 를 조회 조건에 함께 건다. */
    fun findByIdAndCoupleId(id: Long, coupleId: Long): Expense?

    /**
     * 이 반복지출이 해당 기간에 이미 생성됐는지.
     * 사용자가 지운 건(deleted_at)도 "생성됨"으로 본다. 지운 것을 되살리면 안 되기 때문이다.
     */
    fun existsByRecurringExpenseIdAndSpentAtBetween(
        recurringExpenseId: Long,
        from: LocalDate,
        to: LocalDate,
    ): Boolean

    /** 이 반복지출에서 만들어진 지출이 하나라도 있는지. 정의를 지워도 되는지 판단한다. */
    fun existsByRecurringExpenseId(recurringExpenseId: Long): Boolean

    /**
     * 정산이 확정되는 순간 그 달의 지출을 한 번에 귀속시킨다.
     * 이후 이 건들은 settlement_id 가 채워져 수정·삭제가 잠긴다.
     *
     * 건별로 돌면 지출 수만큼 UPDATE 가 나가므로 벌크로 처리하고,
     * 영속성 컨텍스트가 낡은 상태로 남지 않도록 실행 전후로 flush/clear 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update Expense e
        set e.settlementId = :settlementId
        where e.couple.id = :coupleId
          and e.deletedAt is null
          and e.settlementId is null
          and e.spentAt between :from and :to
        """,
    )
    fun lockToSettlement(
        @Param("coupleId") coupleId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("settlementId") settlementId: Long,
    ): Int
}
