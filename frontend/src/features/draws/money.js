// 당첨금 표기. (원 단위 숫자 → 화면 글자)

/** 목록용 짧은 표기: 28.4억원 / 5,172만원 / 5,000원 */
export function shortWon(won) {
  if (won == null || Number.isNaN(won)) return '-'
  if (won >= 100000000) return `${(won / 100000000).toFixed(1).replace(/\.0$/, '')}억원`
  if (won >= 10000) return `${Math.round(won / 10000).toLocaleString('ko-KR')}만원`
  return `${won.toLocaleString('ko-KR')}원`
}

/** 상세용 표기(만원 단위까지): 312억 4,000만원 / 19억 3,580만원 / 148만원 */
export function fullWon(won) {
  if (won == null || Number.isNaN(won)) return '-'
  if (won < 10000) return `${won.toLocaleString('ko-KR')}원`
  const eok = Math.floor(won / 100000000)
  const man = Math.floor((won % 100000000) / 10000)
  const parts = []
  if (eok > 0) parts.push(`${eok.toLocaleString('ko-KR')}억`)
  if (man > 0) parts.push(`${man.toLocaleString('ko-KR')}만`)
  return `${parts.join(' ')}원` // 만원 미만은 버려서 보여 준다 (참고용 표기)
}

export const people = (n) => (n == null ? '-' : `${n.toLocaleString('ko-KR')}명`)
