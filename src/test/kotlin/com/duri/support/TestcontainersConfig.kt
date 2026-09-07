package com.duri.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 테스트는 실제 PostgreSQL 위에서 돈다.
 * H2 를 쓰면 부분 유니크 인덱스·체크 제약·CHAR(6) 같은 것들이 조용히 무시되어
 * "테스트는 통과하는데 운영에서 깨지는" 스키마가 만들어진다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(POSTGRES_IMAGE)

    private companion object {
        /** compose.yaml 과 같은 버전을 쓴다. */
        const val POSTGRES_IMAGE = "postgres:17-alpine"
    }
}
