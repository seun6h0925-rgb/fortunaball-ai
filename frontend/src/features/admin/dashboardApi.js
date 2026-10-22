import { api } from '../../api/http.js'

const ymd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** 대시보드(API-22) + 최근 14일 이용 추이(API-61) + 최근 오류 4건(API-38) + 최신 회차 당첨번호(공개 API)를 한 번에 모은다. */
export async function fetchDashboardAll(signal) {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - 13)
  const [dash, usage, errors] = await Promise.all([
    api.get('/api/admin/dashboard', { signal }),
    api.get('/api/admin/statistics/usage', { signal, query: { from: ymd(from), to: ymd(to) } }),
    api.get('/api/admin/errors', { signal, query: { page: 0, size: 4 } }),
  ])
  let draw = null
  const no = dash?.latestRound?.latestRoundNo
  if (no) {
    try {
      draw = await api.get(`/api/draws/${no}`, { signal })
    } catch (e) {
      if (e?.name === 'AbortError') throw e
    }
  }
  return { dash, usage, errors: errors?.items ?? [], draw, loadedAt: new Date().toISOString() }
}
