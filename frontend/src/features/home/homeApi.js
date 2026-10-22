import { api } from '../../api/http.js'

// 홈 화면이 쓰는 서버 기능 모음. 화면 코드에는 주소를 직접 쓰지 않고 여기만 거친다.
// (주소·변수 이름이 바뀌면 이 파일만 고친다)

/** API-01 최신 회차: { roundNo, drawDate, numbers[6], bonusNumber, prizes[{winningRank, prizeAmount, winnerCount}] } */
export const fetchLatestDraw = (signal) => api.get('/api/draws/latest', { signal })

/** API-43 현재 번호 생성 대상 회차: { targetRound, drawDate, basisRound, nextSwitchAt, statStatus, statStatusLabel } */
export const fetchCurrentRound = (signal) => api.get('/api/rounds/current', { signal })

/**
 * API-11 당첨금 추이: { fromRound, toRound, roundCount, rounds[{roundNo, drawDate, prizes[{rank, prizeAmount, winnerCount}]}] }
 * 범위를 안 주면 최근 20회(최대 200회까지 조회 가능)
 */
export const fetchPrizeTrend = (signal, fromRound, toRound) =>
  api.get('/api/statistics/prizes', { signal, query: { fromRound, toRound } })

/** API-41 지역별 당첨 통계: { fromRound, toRound, previousAvailable, regions[{sido, firstCount, previousFirstCount, firstChangeRate, ...}] } (범위 생략 = 최근 20회) */
export const fetchRegionStats = (signal) => api.get('/api/statistics/regions', { signal })

/** API-66 루키 명당: { items[StoreItem], baseDate, periodMonths, ... } */
export const fetchRookieStores = (signal, limit = 4) => api.get('/api/stores/rookies', { signal, query: { limit } })
