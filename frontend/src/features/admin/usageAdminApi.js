import { api } from '../../api/http.js'

const ymd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const shift = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x }
const SERVICE_START = new Date(2026, 0, 1) // "전체" 는 올해 1월 1일부터

/** days: 1(오늘) / 7 / 30 / 0(전체). 같은 길이의 직전 기간도 함께 받아 증감률을 계산한다. (전체는 비교 없음) */
export async function fetchUsage(signal, days) {
  const today = new Date()
  const from = days === 0 ? SERVICE_START : shift(today, -(days - 1))
  const get = (f, t) => api.get('/api/admin/statistics/usage', { signal, query: { from: ymd(f), to: ymd(t) } })
  const cur = await get(from, today)
  const prev = days === 0 ? null : await get(shift(from, -days), shift(from, -1))
  return { cur, prev }
}
