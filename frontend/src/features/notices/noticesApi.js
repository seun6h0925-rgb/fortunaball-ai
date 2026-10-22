import { api } from '../../api/http.js'

// 공지사항(UI-15)이 쓰는 서버 기능. 로그인 없이 누구나 본다.

export const NOTICE_PAGE_SIZE = 5

/** API-18 공지 목록: { items[{noticeId, title, noticeType, createdAt}], page(0부터), size, totalCount, hasNext } 최신순 */
export const fetchNotices = (signal, page = 0, size = NOTICE_PAGE_SIZE) =>
  api.get('/api/notices', { signal, query: { page, size } })

/** API-19 공지 상세: { noticeId, title, content, noticeType, createdAt } */
export const fetchNotice = (signal, id) => api.get(`/api/notices/${id}`, { signal })

/** 공지 종류 이름표 (일반 공지는 표시하지 않는다) */
export const NOTICE_TAG = { IMPORTANT: '중요', MAINTENANCE: '점검' }

/** "2026-10-15T09:00:00" → "2026.10.15" */
export const noticeDate = (text) => (text ? text.slice(0, 10).replaceAll('-', '.') : '')
