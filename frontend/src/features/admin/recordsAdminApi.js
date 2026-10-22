import { api } from '../../api/http.js'

export const RECORD_PAGE_SIZE = 30

/** 당첨 기록 목록 (API-26). 공개 여부 필터는 'true' / 'false' 문자열로 받는다. */
export const fetchRecords = (signal, { roundNo, rank, publicVisible, page }) =>
  api.get('/api/admin/winning-records', { signal, query: { roundNo, rank, publicVisible, page, size: RECORD_PAGE_SIZE } })

/** 공개 여부 변경 (API-27, CSRF 헤더는 http.js 가 자동으로 붙인다) */
export const setRecordPublic = (recordId, publicVisible) =>
  api.patch(`/api/admin/winning-records/${recordId}`, { publicVisible })

/** 당첨번호(밝은 공 표시용). 실패하면 null */
export async function fetchWinningNumbers(signal, roundNo) {
  try {
    const d = await api.get(`/api/draws/${roundNo}`, { signal })
    return d.numbers
  } catch (e) {
    if (e?.name === 'AbortError') throw e
    return null
  }
}
