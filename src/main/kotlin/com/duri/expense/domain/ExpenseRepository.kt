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
