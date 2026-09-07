package com.duri.realtime.application

import com.duri.realtime.domain.CoupleEvent
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 커플 단위 SSE 연결을 들고 있다가 이벤트를 밀어 준다.
 *
 * 연결은 이 인스턴스의 메모리에만 있다. 서버를 여러 대로 늘리면
 * 다른 인스턴스에 붙은 상대에게는 닿지 않으므로 Redis pub/sub 같은 중계가 필요하다.
 * 2인용 앱에 단일 인스턴스라 지금은 이 단순함이 이득이다.
 */
@Component
class CoupleEventBroker(
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(coupleId: Long, timeoutMillis: Long): SseEmitter {
        val emitter = SseEmitter(timeoutMillis)
        emitters.computeIfAbsent(coupleId) { CopyOnWriteArrayList() }.add(emitter)

        // 어느 경로로 끊기든 목록에서 지운다. 안 그러면 죽은 연결에 계속 쓰려고 한다.
        emitter.onCompletion { remove(coupleId, emitter) }
        emitter.onTimeout { remove(coupleId, emitter) }
        emitter.onError { remove(coupleId, emitter) }

        // 프록시가 첫 바이트를 기다리다 끊는 것을 막고, 클라이언트에 연결 성공을 알린다
        runCatching { emitter.send(SseEmitter.event().name("connected").data("ok")) }
            .onFailure { remove(coupleId, emitter) }

        return emitter
    }

    fun publish(event: CoupleEvent) {
        val targets = emitters[event.coupleId] ?: return
        val payload = objectMapper.writeValueAsString(event)

        targets.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(event.type.name)
                        .data(payload, MediaType.APPLICATION_JSON),
                )
            } catch (e: IOException) {
                // 상대가 탭을 닫은 흔한 경우다. 로그를 시끄럽게 남길 일이 아니다.
                log.debug("sse send failed, dropping emitter: coupleId={}", event.coupleId, e)
                remove(event.coupleId, emitter)
            }
        }
    }

    /** 프록시·로드밸런서가 유휴 연결을 끊지 않도록 주기적으로 주석 한 줄을 보낸다. */
    @Scheduled(fixedDelayString = "\${duri.realtime.heartbeat-millis}")
    fun heartbeat() {
        emitters.forEach { (coupleId, list) ->
            list.forEach { emitter ->
                runCatching { emitter.send(SseEmitter.event().comment("ping")) }
                    .onFailure { remove(coupleId, emitter) }
            }
        }
    }

    fun connectionCount(coupleId: Long): Int = emitters[coupleId]?.size ?: 0

    /** 한 커플의 열린 스트림을 모두 닫는다. 클라이언트는 스스로 다시 붙는다. */
    fun closeAll(coupleId: Long) {
        emitters.remove(coupleId)?.forEach { runCatching { it.complete() } }
    }

    /**
     * 서버가 내려갈 때 열린 스트림을 정리한다.
     * 끊어 주지 않으면 클라이언트가 타임아웃까지 죽은 연결을 붙들고 있는다.
     */
    @PreDestroy
    fun closeAll() {
        emitters.keys.toList().forEach(::closeAll)
    }

    private fun remove(coupleId: Long, emitter: SseEmitter) {
        emitters[coupleId]?.let { list ->
            list.remove(emitter)
            if (list.isEmpty()) emitters.remove(coupleId, list)
        }
    }
}
