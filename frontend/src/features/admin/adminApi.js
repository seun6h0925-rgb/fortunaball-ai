import { api } from '../../api/http.js'

// 관리자 로그인·로그아웃·내 상태 확인 (API-21, API-45, GET /api/admin/auth/me)
export const adminLogin = (loginId, password) => api.post('/api/admin/auth/login', { loginId, password })
export const adminLogout = () => api.post('/api/admin/auth/logout')
export const adminMe = (signal) => api.get('/api/admin/auth/me', { signal })
