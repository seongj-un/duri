/**
 * 서비스워커를 등록한다.
 *
 * 개발 모드에서는 등록하지 않는다. 캐시된 껍데기가 Vite 의 HMR 과 싸워서
 * "고쳤는데 화면이 안 바뀌는" 시간을 만들기 때문이다.
 */
export function registerServiceWorker(): void {
  if (!import.meta.env.PROD) return
  if (!('serviceWorker' in navigator)) return

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // 등록에 실패해도 앱은 그대로 돌아간다. 오프라인에서 안 열릴 뿐이다.
    })
  })
}
