import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// 백엔드가 CORS 허용 오리진·OAuth 착지·초대 링크를 모두 localhost:3000 으로 잡아 두었다.
// 같은 오리진으로 프록시하면 리프레시 쿠키(path=/api/v1/auth, SameSite=Lax)가
// 자격증명 설정 없이 그대로 실려 간다.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.VITE_BACKEND_ORIGIN || 'http://localhost:8080'

  const proxy = {
    '/api': backend,
    // 소셜 로그인 핸드셰이크. 카카오·구글이 돌아오는 곳은 백엔드에 직접 등록돼 있어
    // 여기 프록시는 로그인 시작(302) 만 넘긴다.
    '/oauth2': backend,
    '/login/oauth2': backend,
  }

  return {
    plugins: [react()],
    server: { port: 3000, strictPort: true, proxy },
    // 서비스워커는 프로덕션 빌드에서만 등록된다. preview 에도 같은 프록시를 걸어야
    // 빌드 산출물을 백엔드와 함께 그대로 확인할 수 있다.
    preview: { port: 4173, strictPort: true, proxy },
  }
})
