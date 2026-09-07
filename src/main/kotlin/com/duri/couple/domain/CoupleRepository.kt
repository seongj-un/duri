package com.duri.couple.domain

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CoupleRepository : JpaRepository<Couple, Long> {

    /**
     * 정산 확정처럼 "커플 단위로 한 번에 하나만" 돌아야 하는 작업의 진입점.
     * 두 사람이 동시에 확정 버튼을 눌러도 여기서 줄을 선다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Couple c where c.id = :coupleId")
    fun findByIdForUpdate(@Param("coupleId") coupleId: Long): Couple?
}
