// 비회원 식별값. 처음 접속할 때 브라우저가 만들어 저장하고, 모든 요청의 X-Guest-Token 헤더로 보낸다.
// 서버 규칙: 영문·숫자·하이픈 16~128자. (UUID 형식이면 통과)
const KEY = 'fortunaball.guestToken'
const PATTERN = /^[A-Za-z0-9-]{16,128}$/
let cached = null

function randomToken() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16)).join('')
}

export function getGuestToken() {
  if (cached) return cached
  try {
    const saved = localStorage.getItem(KEY)
    if (saved && PATTERN.test(saved)) {
      cached = saved
      return cached
    }
  } catch {
    /* 저장소를 못 쓰는 환경(시크릿 모드 등)이면 새로 만든다 */
  }
  cached = randomToken()
  try {
    localStorage.setItem(KEY, cached)
  } catch {
    /* 저장 실패는 무시: 이번 접속 동안만 같은 값을 쓴다 */
  }
  return cached
}
