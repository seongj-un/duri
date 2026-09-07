package com.duri.realtime.application

import com.duri.realtime.domain.CoupleEvent
import com.duri.realtime.domain.CoupleEventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 도메인 서비스가 SSE 를 직접 알지 않게 하는 얇은 층.
 *
 * 여기서 발행한 이벤트는 트랜잭션이 커밋된 뒤에야 밖으로 나간다.
 * 롤백된 변경을 상대 화면에 알리면 있지도 않은 지출이 보인다.
 */
@Component
class CoupleEventPublisher(
    private val publisher: ApplicationEventPublisher,
    private val clock: Clock,
) {

    fun publish(
        type: CoupleEventType,
        coupleId: Long,
        actorId: Long? = null,
        resourceId: Long? = null,
        period: YearMonth? = null,
    ) {
        publisher.publishEvent(
            CoupleEvent(
                type = type,
                coupleId = coupleId,
                actorId = actorId,
                resourceId = resourceId,
                period = period?.format(PERIOD_FORMAT),
                occurredAt = clock.instant(),
            ),
        )
    }

    private companion object {
        val PERIOD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM")
    }
}
