// 방금 만든 번호(임시 결과)를 화면 사이에서 넘기는 보관함.
// 생성 화면(UI-02) → 결과 화면(UI-03)으로 가는 동안, 그리고 결과 화면에서 새로고침해도 번호가 남게
// 브라우저의 sessionStorage(탭을 닫으면 사라지는 저장소)에 둔다.
// 서버도 30분만 들고 있으므로, 만료 시각(expiresAt)이 지난 결과는 없는 것으로 친다.
const KEY = 'fortunaball.generated'

export function saveGenerated(data) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(data))
  } catch {
    /* 저장소를 못 쓰는 환경이면 건너뛴다. (결과 화면은 "만든 번호가 없어요"로 안내한다) */
  }
}

export function loadGenerated() {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (!data?.expiresAt || Date.parse(data.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(KEY)
      return null
    }
    return data
  } catch {
    return null
  }
}

export function clearGenerated() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* 무시 */
  }
}

// 발급이 끝난 결과(발급 코드 포함). 발급 완료 화면(UI-03B)이 읽는다. 만료는 없다. (이미 저장된 번호이므로)
const ISSUED_KEY = 'fortunaball.issued'

export function saveIssued(data) {
  try {
    sessionStorage.setItem(ISSUED_KEY, JSON.stringify(data))
  } catch {
    /* 무시 */
  }
}

export function loadIssued() {
  try {
    const raw = sessionStorage.getItem(ISSUED_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}
