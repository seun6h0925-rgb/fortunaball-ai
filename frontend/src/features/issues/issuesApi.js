import { api } from '../../api/http.js'

// 발급 내역 화면(UI-04)이 쓰는 서버 기능 모음.

/**
 * API-42 이 기기의 발급 내역 목록 (최신 발급이 먼저). 게스트 식별값으로 "이 기기" 것만 온다.
 * 받는 값: { items[{issueCode, targetRound, gameCount, issuedAt, patternCode, patternName,
 *                  verificationStatus, verificationLabel, firstGame[6]}], page, size, totalCount, totalPages }
 */
export const fetchIssueList = (signal, page = 0, size = 10) =>
  api.get('/api/issues', { signal, query: { page, size } })

/**
 * API-05 발급 코드로 상세 조회. 코드만 알면 다른 기기 것도 볼 수 있다. (조회 횟수 제한이 있어 너무 잦으면 429)
 * 받는 값: { issueCode, targetRound, issuedAt, gameCount, patternCode, patternName,
 *           games[{gameOrder, numbers[6]}], verificationStatus, verificationLabel }
 */
export const fetchIssueDetail = (code) => api.get(`/api/issues/${encodeURIComponent(code)}`)

/**
 * API-67 이 기기의 발급 내역 목록에서 지우기. 서버 기록은 남고 이 기기 목록에서만 빠진다. (코드로는 계속 조회 가능)
 * 받는 값: { issueCode }
 */
export const hideIssue = (code) => api.del(`/api/issues/${encodeURIComponent(code)}`)
