import { api } from '../../api/http.js'

export const VERIFY_PAGE_SIZE = 30

/** 위쪽 카드 숫자: 전체 발급(API-24), 검증 대기·검증 실패(API-48)의 건수만 쓴다. */
export async function fetchVerifyCounts(signal, roundNo) {
  const one = { page: 0, size: 1, roundNo }
  const [all, pending, error] = await Promise.all([
    api.get('/api/admin/issues', { signal, query: one }),
    api.get('/api/admin/issues/unverified', { signal, query: { ...one, status: 'PENDING' } }),
    api.get('/api/admin/issues/unverified', { signal, query: { ...one, status: 'ERROR' } }),
  ])
  return { total: all.totalCount, pending: pending.totalCount, error: error.totalCount }
}

/** tab: result(당첨 검증 결과, API-26) | pending | error (API-48) */
export function fetchVerifyList(signal, { tab, roundNo, page }) {
  const base = { roundNo, page, size: VERIFY_PAGE_SIZE }
  if (tab === 'result') return api.get('/api/admin/winning-records', { signal, query: base })
  return api.get('/api/admin/issues/unverified', { signal, query: { ...base, status: tab === 'pending' ? 'PENDING' : 'ERROR' } })
}

/** 검증 수동 실행 (API-25). 서버가 미검증 건을 모두 다시 판정한다. */
export const runVerify = () => api.post('/api/admin/issues/verify')
