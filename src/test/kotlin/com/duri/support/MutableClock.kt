package com.duri.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 만료·회전처럼 시간이 규칙인 로직을 검증하기 위해 시간을 직접 움직인다. */
class MutableClock(
    private var instant: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock() {

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    override fun instant(): Instant = instant

    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }

    fun reset() {
        instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
