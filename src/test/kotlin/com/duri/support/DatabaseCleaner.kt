package com.duri.support

import jakarta.persistence.EntityManager
import org.springframework.boot.test.context.TestComponent
import org.springframework.transaction.annotation.Transactional

/**
 * 테스트 사이에 데이터를 비운다.
 *
 * 테스트를 @Transactional 로 감싸 롤백시키지 않는 이유: 이 프로젝트의 핵심 규칙 상당수가
 * DB 제약(부분 유니크 인덱스, 체크 제약)과 커밋 시점 동작에 걸려 있어서,
 * 실제로 커밋해 봐야 검증이 된다.
 */
@TestComponent
class DatabaseCleaner(
    private val entityManager: EntityManager,
) {

    @Transactional
    fun clean() {
        @Suppress("UNCHECKED_CAST")
        val tables = entityManager.createNativeQuery(
            """
            select table_name from information_schema.tables
            where table_schema = 'public' and table_type = 'BASE TABLE'
              and table_name <> 'flyway_schema_history'
            """,
        ).resultList as List<String>

        if (tables.isEmpty()) return
        val quoted = tables.joinToString(", ") { "\"$it\"" }
        entityManager.createNativeQuery("truncate table $quoted restart identity cascade").executeUpdate()
    }
}
