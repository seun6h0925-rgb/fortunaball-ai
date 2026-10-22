import { api } from '../../api/http.js'

export const NOTICE_PAGE_SIZE = 10
export const NOTICE_TYPES = { GENERAL: '일반', IMPORTANT: '중요', MAINTENANCE: '점검' }

/** 탭 → 서버 콘텐츠 종류 (API-58 의 contentType). 한 탭에 여러 종류가 들어가는 곳이 있다. */
export const CONTENT_TABS = [
  { key: 'intro', label: '서비스 안내', types: ['SERVICE_INTRO', 'USAGE_GUIDE'] },
  { key: 'notice', label: '유의사항', types: ['PRIVACY', 'AI_NOTICE', 'NO_GUARANTEE', 'GAMBLING_WARNING', 'DISCLAIMER'] },
  { key: 'claim', label: '수령 가이드', types: ['CLAIM_GUIDE'] },
  { key: 'faq', label: 'FAQ', types: ['FAQ'] },
]

// 공지 (API-60, 35, 36, 37)
export const fetchAdminNotices = (signal, page) => api.get('/api/admin/notices', { signal, query: { page, size: NOTICE_PAGE_SIZE } })
export const createNotice = (body) => api.post('/api/admin/notices', body)
export const updateNotice = (id, body) => api.patch(`/api/admin/notices/${id}`, body)
export const deleteNotice = (id) => api.del(`/api/admin/notices/${id}`)

// 안내 콘텐츠 (API-58, 59)
export const fetchAdminContents = (signal) => api.get('/api/admin/contents', { signal })
export const updateContent = (type, body) => api.patch(`/api/admin/contents/${type}`, body)
