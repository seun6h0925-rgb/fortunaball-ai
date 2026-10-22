/** 게임 이름: A, B, C ... Z, 그 뒤로는 27, 28 ... (게임은 최대 50개까지 만들 수 있다) */
export function gameLabel(index) {
  return index < 26 ? `게임 ${String.fromCharCode(65 + index)}` : `게임 ${index + 1}`
}
