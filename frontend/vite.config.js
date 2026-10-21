import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 서버(5173)에서 /api 로 보내는 요청을 백엔드(8080)로 넘겨준다.
// 브라우저에서는 같은 주소로 보이므로 쿠키(관리자 로그인)와 CORS 문제가 생기지 않는다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
})
