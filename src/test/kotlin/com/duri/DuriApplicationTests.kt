package com.duri

import com.duri.support.IntegrationTestBase
import org.junit.jupiter.api.Test

/**
 * 컨텍스트가 뜨는 것만 보는 테스트처럼 보이지만, 실제로는 두 가지를 검증한다.
 *  - Flyway 마이그레이션이 빈 PostgreSQL 에 처음부터 끝까지 적용되는가
 *  - hibernate.ddl-auto=validate 가 통과하는가 (= 엔티티 매핑과 스키마가 정확히 일치하는가)
 */
class DuriApplicationTests : IntegrationTestBase() {

    @Test
    fun `애플리케이션 컨텍스트가 뜨고 스키마와 엔티티 매핑이 일치한다`() {
        // 컨텍스트 로딩 자체가 검증이다
    }
}
