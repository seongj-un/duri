import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { App } from './App'
import { ApiError } from './lib/api/client'
import { SessionProvider } from './lib/auth/SessionProvider'
import { registerServiceWorker } from './lib/pwa/registerServiceWorker'
import './styles/global.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 4xx 는 다시 물어봐야 답이 같다. 서버·네트워크 문제만 한 번 더 시도한다.
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status < 500) return false
        return failureCount < 1
      },
      staleTime: 30 * 1000,
      // 상대가 넣은 지출은 SSE 로 따라오지만, 스트림이 끊겼을 때의 안전망이다.
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <App />
        </SessionProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>,
)

registerServiceWorker()
