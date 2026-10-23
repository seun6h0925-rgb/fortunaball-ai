import { api } from '../../api/http.js'

// 우리 서비스 당첨 기록(UI-09)이 쓰는 서버 기능. 로그인 없이 누구나 본다.

/**
 * API-17 우리 서비스 당첨 기록 (공개된 기록만). 최신 회차 → 높은 등수 순.
 * rank 를 비우면 전체. size 는 최대 50.
 * 받는 값: { items[{recordId, roundNo, rank, matchCount, bonusMatched, issueCode, guestMasked, issuedAt, comment, verifiedAt}], page, size, totalElements, totalPages }
 * ※ 발급코드·발급 시각(초 단위)·게스트(앞 4자리만 가림)는 그대로 공개한다. 발급 번호·사진은 내려오지 않는다.
 */
export const fetchRecords = (signal, { rank, page = 0, size = 10 } = {}) =>
  api.get('/api/winning-records', { signal, query: { rank, page, size } })

/** 등수 필터 칩 (null = 전체) */
export const RANK_FILTERS = [null, 1, 2, 3, 4, 5]

/** 한 건의 조건 문구: "5개 일치 + 보너스" */
export function matchText(item) {
  return `${item.matchCount}개 일치${item.bonusMatched ? ' + 보너스' : ''}`
}
