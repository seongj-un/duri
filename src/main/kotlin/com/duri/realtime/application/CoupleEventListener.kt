package com.duri.realtime.application

import com.duri.realtime.domain.CoupleEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CoupleEventListener(
    private val broker: CoupleEventBroker,
) {

    /**
     * 커밋된 뒤에만 내보낸다. 롤백된 변경은 상대에게 알리지 않는다.
     * 트랜잭션 밖에서 발행된 이벤트도(fallbackExecution) 그대로 전달한다.
     *
     * 비동기로 돌려 SSE 로 밀어 넣는 I/O 가 요청 응답 시간에 섞이지 않게 한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: CoupleEvent) {
        broker.publish(event)
    }
}
