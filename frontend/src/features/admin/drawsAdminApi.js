import { api } from '../../api/http.js'

export const LIST_COUNT = 60 // 한 번에 불러오는 최근 회차 수 (서버 한도 200)

/** 최신 회차를 알아낸 뒤, 최근 60회 + 다음 회차 1개의 상태를 가져온다. (API-22, API-46) */
export async function fetchRoundList(signal) {
  const dash = await api.get('/api/admin/dashboard', { signal })
  const latest = dash?.latestRound?.latestRoundNo || 0
  if (!latest) return { latest: 0, items: [] }
  const items = await api.get('/api/admin/draws', { signal, query: { fromRound: Math.max(1, latest - LIST_COUNT + 1), toRound: latest + 1 } })
  return { latest, items }
}

/** 화면에 보이는 회차들의 당첨번호만 공개 API 로 가져온다. 실패한 회차는 건너뛴다. */
export async function fetchNumbers(signal, roundNos) {
  const out = {}
  await Promise.all(roundNos.map(async (no) => {
    try {
      const d = await api.get(`/api/draws/${no}`, { signal })
      out[no] = { numbers: d.numbers, bonus: d.bonusNumber }
    } catch (e) {
      if (e?.name === 'AbortError') throw e
    }
  }))
  return out
}

/** 1회~최신 회차 전체에서 DB 에 없는 회차 번호를 찾는다. (서버가 200회씩만 보여 줘서 나눠서 묻는다) */
export async function fetchMissingAll(latest) {
  const missing = []
  for (let from = 1; from <= latest; from += 200) {
    const to = Math.min(latest, from + 199)
    const items = await api.get('/api/admin/draws', { query: { fromRound: from, toRound: to } })
    items.filter((i) => i.missing).forEach((i) => missing.push(i.roundNo))
  }
  return missing.sort((a, b) => a - b)
}

export const resyncRound = (round) => api.post(`/api/admin/draws/${round}/resync`)
export const recalcRound = (round) => api.post(`/api/admin/statistics/${round}/recalculate`)
