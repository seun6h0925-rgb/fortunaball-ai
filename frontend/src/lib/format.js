// 화면에 보여 주는 값의 모양을 한곳에서 정한다. (숫자·날짜 표기를 화면마다 다르게 쓰지 않기 위함)

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 원 단위 금액 → "28.4억" (소수 첫째 자리까지). 값이 없으면 "-" */
export function formatEok(won) {
  if (won === null || won === undefined || Number.isNaN(won)) return '-'
  return `${(won / 100000000).toFixed(1)}억`
}

/** 억 단위 숫자(이미 억으로 나눈 값) → "28.4억" */
export function formatEokValue(eok) {
  return `${eok.toFixed(1)}억`
}

/** "2026-10-10" → "2026.10.10 (토)". 날짜만 있는 글자는 시간대 때문에 하루가 밀리지 않게 로컬 시각으로 해석한다 */
export function formatDrawDate(dateText) {
  if (!dateText) return ''
  const [y, m, d] = dateText.split('-').map(Number)
  const day = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  return `${y}.${String(m).padStart(2, '0')}.${String(d).padStart(2, '0')} (${day})`
}

/** 증감률(%) → "12.3%" (부호는 화살표로 따로 보여 주므로 절댓값) */
export function formatRate(rate) {
  return `${Math.abs(rate).toFixed(1)}%`
}

/** "2026-10-15T13:42:10" → "2026.10.15 (목) 13:42". 서버가 한국 시간으로 주는 값이라 시간대 변환 없이 글자 그대로 읽는다 */
export function formatDateTime(text) {
  if (!text) return ''
  const [datePart, timePart = ''] = text.split('T')
  const [y, m, d] = datePart.split('-').map(Number)
  const day = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  const hhmm = timePart.slice(0, 5)
  return `${y}.${String(m).padStart(2, '0')}.${String(d).padStart(2, '0')} (${day}) ${hhmm}`
}
