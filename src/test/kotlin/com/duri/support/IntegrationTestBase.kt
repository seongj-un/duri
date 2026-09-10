package com.duri.support

import com.duri.auth.application.AuthRateLimiter
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import java.time.Clock

// 스케줄러는 테스트에서 직접 호출한다. "-" 는 스프링에서 비활성화를 뜻한다.
@SpringBootTest(properties = ["duri.recurring.cron=-", "duri.notification.reminder-cron=-"])
@Import(TestcontainersConfig::class, TestClockConfig::class, DatabaseCleaner::class, CoupleFixture::class)
// 하위 테스트가 생성자 파라미터마다 @Autowired 를 달지 않아도 되게 한다
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class IntegrationTestBase {

    @Autowired
    protected lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    protected lateinit var clock: Clock

    /** 인메모리 카운터라 컨텍스트를 공유하는 테스트끼리 새어 나간다. 테스트마다 비운다. */
    @Autowired
    protected lateinit var authRateLimiter: AuthRateLimiter

    protected val mutableClock: MutableClock get() = clock as MutableClock

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
        authRateLimiter.clearAll()
        mutableClock.reset()
    }
}
