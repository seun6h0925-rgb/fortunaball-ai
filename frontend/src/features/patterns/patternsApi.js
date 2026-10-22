import { api } from '../../api/http.js'

// 패턴 모아보기(UI-02B)가 쓰는 서버 기능.
// 기준 회차는 서버가 정한다. 화면은 기간(20·50·100회)만 고른다.

export const PERIODS = [20, 50, 100]
export const DEFAULT_PERIOD = 100

/**
 * API-44 통계 패턴 카드 조회
 * 받는 값: { targetRound, basisRound, status, statusLabel, period, generatedAt,
 *           patterns[{ code, name, description, period, hitCount, exampleNumbers }] }
 * exampleNumbers: "구성 예시" 공 6개. 서버가 그 패턴 조건으로 실제 생성한 번호이고, 만들지 못했으면 null 이다.
 */
export const fetchPatterns = (signal, period) => api.get('/api/statistics/patterns', { signal, query: { period } })
