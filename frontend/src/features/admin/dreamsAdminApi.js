import { api } from '../../api/http.js'

export const DREAM_PAGE_SIZE = 30
export const ORIGINS = { SERVICE: '서비스', SMORFIA: '나폴리', CHARADA: '쿠바' }

/** 꿈 상징 목록 (API-57). active 는 'true' / 'false' / '' */
export const fetchSymbols = (signal, { keyword, active, page }) =>
  api.get('/api/admin/dream-symbols', { signal, query: { keyword, active, page, size: DREAM_PAGE_SIZE } })

/** 위쪽 카드용 개수: 전체 / 사용 중 / 사용 안 함 */
export async function fetchSymbolCounts(signal) {
  const c = (active) => api.get('/api/admin/dream-symbols', { signal, query: { active, page: 0, size: 1 } }).then((r) => r.totalCount)
  const [all, on, off] = await Promise.all([c(undefined), c('true'), c('false')])
  return { all, on, off }
}

export const createSymbol = (body) => api.post('/api/admin/dream-symbols', body)
export const updateSymbol = (id, body) => api.patch(`/api/admin/dream-symbols/${id}`, body)
