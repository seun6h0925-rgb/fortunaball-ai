import { api } from '../../api/http.js'

export const ISSUE_PAGE_SIZE = 30

/** 발급 기록 목록 (API-24). 비어 있는 조건은 http.js 가 알아서 뺀다. */
export const fetchIssues = (signal, { roundNo, issueCode, guestToken, page }) =>
  api.get('/api/admin/issues', { signal, query: { roundNo, issueCode, guestToken, page, size: ISSUE_PAGE_SIZE } })

/** 회차 선택 칸에 넣을 회차 목록: 다음 회차부터 거꾸로 30개 */
export async function fetchRoundChoices(signal) {
  const dash = await api.get('/api/admin/dashboard', { signal })
  const next = dash?.latestRound?.nextRoundNo
  if (!next) return []
  return Array.from({ length: Math.min(30, next) }, (_, i) => next - i)
}
