package com.duri.realtime.api

import com.duri.common.web.CurrentUserId
import com.duri.couple.application.CoupleContextLoader
import com.duri.realtime.application.CoupleEventBroker
import com.duri.realtime.config.RealtimeProperties
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/events")
class EventStreamController(
    private val broker: CoupleEventBroker,
    private val coupleContextLoader: CoupleContextLoader,
    private val properties: RealtimeProperties,
) {

    /**
     * 커플 space 의 변경 스트림.
     *
     * 브라우저 기본 EventSource 는 헤더를 못 붙이므로 프론트는 fetch 기반 SSE 를 쓴다
     * (예: @microsoft/fetch-event-source). 토큰을 쿼리스트링에 실으면
     * 주소창·프록시 로그에 남기 때문에 그 방식은 쓰지 않는다.
     */
    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(@CurrentUserId userId: Long): SseEmitter {
        val context = coupleContextLoader.loadActive(userId)
        return broker.subscribe(context.coupleId, properties.timeout.toMillis())
    }
}
