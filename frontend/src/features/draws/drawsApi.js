import { api } from '../../api/http.js'

// 회차별 당첨번호(UI-08)가 쓰는 서버 기능.
// 최신 회차 조회(API-01)는 홈과 같은 것을 쓴다: import { fetchLatestDraw } from '../home/homeApi.js'

/** API-02 회차별 당첨번호: { roundNo, drawDate, numbers[6], bonusNumber, prizes[{winningRank, prizeAmount, winnerCount}] } */
export const fetchDraw = (signal, round) => api.get(`/api/draws/${round}`, { signal })

/**
 * API-15 당첨금 세금 계산(참고용): 1~3등만 세금이 붙는다. 금액은 모두 원 단위.
 * 받는 값: { roundNo, rank, rankLabel, winnerCount, prizeAmount, taxFree, incomeTax, localIncomeTax,
 *           totalTax, netAmount, rateLabel, policyDate, sourceName, sourceUrl, notice }
 */
export const calcPayout = (signal, roundNo, rank) => api.post('/api/payout/calculate', { roundNo, rank }, { signal })

/** 등수별 조건 문구 (로또 6/45 규칙) */
export const RANK_RULES = {
  1: '6개 일치',
  2: '5개 + 보너스',
  3: '5개 일치',
  4: '4개 일치',
  5: '3개 일치',
}

/** 세금 계산을 보여 주는 등수 (4·5등은 비과세라 펼치지 않는다) */
export const TAXED_RANKS = [1, 2, 3]
