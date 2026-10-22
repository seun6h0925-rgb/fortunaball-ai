import { api } from '../../api/http.js'

// 통계 분석(UI-07)이 쓰는 서버 기능과 탭 목록.
// 모든 수치는 "과거에 나온 결과를 정리한 사실"이다. 예측 값은 없다.

/** 탭 6개. code 는 서버의 패턴 코드와 같다. ([이 통계로 번호 생성]이 /generate?pattern=코드 로 이어진다) */
export const TABS = [
  { code: 'HOT_NUMBER', key: 'hot', label: '핫넘버', make: '핫넘버' },
  { code: 'WINTER_SLEEP', key: 'winter', label: '겨울잠 번호', make: '겨울잠 번호' },
  { code: 'BALANCE_MIX', key: 'balance', label: '밸런스 믹스', make: '밸런스 믹스' },
  { code: 'HIGH_LOW_MIX', key: 'highlow', label: '하이로우 믹스', make: '하이로우 믹스' },
  { code: 'CENTER_ZONE', key: 'center', label: '센터 존', make: '센터 존' },
  { code: 'ALL_SECTION_SPREAD', key: 'section', label: '올 구간 스프레드', make: '올 구간 스프레드' },
]

/** 기간 칩. 값은 API-09/10 의 period 와 같다. (패턴 카드 기간은 20·50·100 이라 PATTERN_PERIOD 로 바꿔 쓴다) */
export const PERIODS = [
  { value: '10', label: '최근 10회' },
  { value: '50', label: '최근 50회' },
  { value: '100', label: '최근 100회' },
  { value: 'ALL', label: '전체' },
]
export const DEFAULT_PERIOD = '100'

/** 기간 → 패턴 카드(API-44)의 기간. 패턴 카드는 20·50·100 만 있어서 가장 가까운 값을 쓴다. */
export const PATTERN_PERIOD = { 10: 20, 50: 50, 100: 100, ALL: 100 }

export const periodText = (p) => (p === 'ALL' ? '전체' : `최근 ${p}회`)

/** API-09 통계 요약. basisRound 를 안 보내면 서버가 가장 최근 회차로 계산한다. */
export const fetchSummary = (signal, period, basisRound) =>
  api.get('/api/statistics/summary', { signal, query: { period, basisRound } })

/** API-10 번호별 출현 횟수 (1~45). 요약이 알려 준 회차 범위로 부른다. */
export const fetchNumbers = (signal, fromRound, toRound) =>
  api.get('/api/statistics/numbers', { signal, query: { fromRound, toRound } })

/** API-44 패턴 카드 (설명과 "최근 N회 중 M회 해당") */
export const fetchPatterns = (signal, period) => api.get('/api/statistics/patterns', { signal, query: { period } })
