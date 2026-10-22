import { api } from '../../api/http.js'

const ymd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** days: 1(오늘) / 7 / 30 → 시작·끝 날짜 */
function range(days) {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - (days - 1))
  return { from: ymd(from), to: ymd(to) }
}

/**
 * AI 운영 화면에 필요한 값을 한 번에 모은다. (API-32)
 *  - all: 기간 요약·한도·최근 기록(응답 시간 평균용 100건)
 *  - analyst / dream: 기능별 호출 수(총 건수만 쓰므로 size=1)
 *  - failed: 실패 기록 10건
 */
export async function fetchAiOps(signal, days) {
  const r = range(days)
  const q = (extra) => api.get('/api/admin/ai/logs', { signal, query: { ...r, ...extra } })
  const [all, analyst, dream, failed] = await Promise.all([
    q({ page: 0, size: 100 }),
    q({ featureType: 'ANALYST', page: 0, size: 1 }),
    q({ featureType: 'DREAM', page: 0, size: 1 }),
    q({ status: 'FAILED', page: 0, size: 10 }),
  ])
  return { all, analyst: analyst.logs.totalCount, dream: dream.logs.totalCount, failed: failed.logs.items, failedTotal: failed.logs.totalCount }
}
