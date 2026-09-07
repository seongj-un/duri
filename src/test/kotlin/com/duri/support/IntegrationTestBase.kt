package com.duri.support

import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Clock

@SpringBootTest
@Import(TestcontainersConfig::class, TestClockConfig::class, DatabaseCleaner::class)
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
