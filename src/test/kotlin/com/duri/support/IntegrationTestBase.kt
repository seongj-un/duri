package com.duri.support

import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import java.time.Clock

@SpringBootTest
@Import(TestcontainersConfig::class, TestClockConfig::class, DatabaseCleaner::class, CoupleFixture::class)
// 하위 테스트가 생성자 파라미터마다 @Autowired 를 달지 않아도 되게 한다
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class IntegrationTestBase {

    @Autowired
    protected lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    protected lateinit var clock: Clock

    protected val mutableClock: MutableClock get() = clock as MutableClock

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
        mutableClock.reset()
    }
}
