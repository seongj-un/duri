/*
 * PairPay 서비스워커.
 *
 * 규칙 하나가 나머지를 결정한다: **API 응답은 절대 캐시하지 않는다.**
 * 가계부에서 오래된 금액을 보여주는 건 아무것도 안 보여주는 것보다 나쁘다.
 * 오프라인일 때 "지난주 순잔액"이 아무 표시 없이 떠 있으면 그걸로 송금하게 된다.
 *
 * 그래서 캐시는 앱 껍데기(HTML·JS·CSS·아이콘)까지만 담당한다.
 * 껍데기는 오프라인에서도 떠서 "연결이 필요해요" 화면을 보여줄 수 있고,
 * 숫자는 언제나 네트워크에서 온 것만 보인다.
 */

// 캐시 내용을 갈아엎어야 할 때 이 숫자를 올린다.
const CACHE = 'pairpay-shell-v1'

const APP_SHELL = '/index.html'

// 설치 즉시 껍데기를 확보한다. 빌드 산출물(/assets/*)은 이름이 해시라 미리 알 수 없어
// 처음 받아올 때 캐시에 넣는다(아래 fetch 핸들러).
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll([APP_SHELL, '/manifest.webmanifest', '/icon-192.png']))
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return

  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return

  // API·인증·SSE 는 손대지 않는다. 캐시하지도, 오프라인 대체물을 주지도 않는다.
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/oauth2/') || url.pathname.startsWith('/login/')) {
    return
  }

  // 화면 이동: 새 껍데기를 먼저 시도하고, 오프라인이면 캐시된 껍데기로 연다.
  // SPA 라 어떤 경로로 들어와도 index.html 하나면 된다.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone()
          void caches.open(CACHE).then((cache) => cache.put(APP_SHELL, copy))
          return response
        })
        .catch(() => caches.match(APP_SHELL).then((cached) => cached ?? Response.error())),
    )
    return
  }

  // 정적 자산: 파일명에 해시가 박혀 있어 내용이 바뀌면 이름도 바뀐다. 캐시를 먼저 본다.
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached
      return fetch(request).then((response) => {
        if (response.ok && response.type === 'basic') {
          const copy = response.clone()
          void caches.open(CACHE).then((cache) => cache.put(request, copy))
        }
        return response
      })
    }),
  )
})
