import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source'
import { API_BASE, refreshAccessToken } from '../api/client'
import { getAccessToken, isAccessTokenUsable } from '../auth/tokenStore'
import { queryKeys } from '../queryKeys'
import type { CoupleEvent } from '../api/types'

/** 되살릴 수 없는 실패. 다시 붙어 봐야 같은 결과라 스트림을 접는다. */
class FatalStreamError extends Error {}

const RETRY_DELAY_MS = 3000

/**
 * 커플 space 의 변경 스트림에 붙는다.
 *
 * 브라우저 기본 EventSource 는 헤더를 못 붙여 Bearer 인증이 안 된다.
 * 토큰을 쿼리스트링에 실으면 주소창·프록시 로그에 남으므로 fetch 기반 SSE 를 쓴다.
 *
 * 이벤트에는 데이터가 실려 오지 않는다. "어느 달이 바뀌었는지"만 보고
 * 그 달의 쿼리를 무효화하면 TanStack Query 가 알아서 다시 불러온다.
 */
export function useCoupleEvents(enabled: boolean, myUserId: number | undefined) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!enabled) return

    const controller = new AbortController()
    const headers: Record<string, string> = { Accept: 'text/event-stream' }

    const applyToken = () => {
      const token = getAccessToken()
      if (token) headers.Authorization = `Bearer ${token}`
    }

    const run = async () => {
      if (!isAccessTokenUsable()) await refreshAccessToken()
      applyToken()

      try {
        await fetchEventSource(`${API_BASE}/api/v1/events`, {
          headers,
          signal: controller.signal,
          // 탭을 백그라운드로 보내도 끊지 않는다. 상대가 넣은 지출이 돌아왔을 때 이미 반영돼 있어야 한다.
          openWhenHidden: true,

          async onopen(response) {
            if (response.ok && response.headers.get('content-type')?.includes(EventStreamContentType)) {
              return
            }
            if (response.status === 401) {
              // 액세스 토큰이 만료된 것뿐이다. 갱신하고 다시 붙는다.
              const outcome = await refreshAccessToken()
              // 리프레시 토큰이 거절당했을 때만 접는다. 네트워크가 흔들린 것이면 잠시 뒤 다시 붙으면 된다.
              if (outcome === 'expired') throw new FatalStreamError('세션이 만료되었습니다.')
              if (outcome === 'offline') throw new Error('재발급이 서버에 닿지 못했습니다. 다시 연결합니다.')
              applyToken()
              throw new Error('토큰을 갱신했습니다. 다시 연결합니다.')
            }
            // 커플이 아직 없거나(409) 권한이 없으면(403) 다시 붙어도 같다.
            if (response.status >= 400 && response.status < 500) {
              throw new FatalStreamError(`스트림을 열 수 없습니다: ${response.status}`)
            }
            throw new Error(`스트림 연결 실패: ${response.status}`)
          },

          onmessage(message) {
            // 서버가 연결 직후 보내는 인사. 페이로드가 JSON 이 아니다.
            if (!message.event || message.event === 'connected') return

            const event = JSON.parse(message.data) as CoupleEvent
            // 내가 일으킨 변화는 내 화면에 이미 반영돼 있다.
            if (event.actorId !== null && event.actorId === myUserId) return

            invalidateFor(queryClient, event)
          },

          onerror(error) {
            if (error instanceof FatalStreamError) throw error
            // 그 외에는 네트워크가 흔들린 것으로 보고 다시 붙는다.
            return RETRY_DELAY_MS
          },
        })
      } catch {
        // 스트림이 끊겨도 앱은 계속 쓸 수 있다. 화면이 자동으로 안 따라올 뿐이다.
      }
    }

    void run()
    return () => controller.abort()
  }, [enabled, myUserId, queryClient])
}

function invalidateFor(queryClient: QueryClient, event: CoupleEvent) {
  const { period } = event

  const invalidateMonth = () => {
    if (period) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.expensesOfPeriod(period) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.summary(period) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.settlement(period) })
      return
    }
    // 어느 달인지 모르면 전부 턴다. 드문 경우라 비용을 감수한다.
    void queryClient.invalidateQueries({ queryKey: ['expenses'] })
    void queryClient.invalidateQueries({ queryKey: ['summary'] })
    void queryClient.invalidateQueries({ queryKey: ['settlement'] })
  }

  switch (event.type) {
    case 'EXPENSE_CREATED':
    case 'EXPENSE_UPDATED':
    case 'EXPENSE_DELETED':
    case 'RECURRING_EXPENSE_GENERATED':
      invalidateMonth()
      break
    case 'SETTLEMENT_CONFIRMED':
      invalidateMonth()
      void queryClient.invalidateQueries({ queryKey: queryKeys.settlementHistory })
      break
    case 'NOTIFICATION_CREATED':
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications })
      break
  }
}
