// 발급 코드 표기를 한곳에서 정한다. (서버 코드는 하이픈 없는 영문·숫자 12자)

/** "FB7K29QXM4AB" → "FB7K-29QX-M4AB" (읽기 쉽게 네 글자씩 끊어 보여 준다. 복사할 때는 끊지 않은 원래 값) */
export function groupCode(code) {
  return code.replace(/(.{4})(?=.)/g, '$1-')
}

/** 사용자가 입력·붙여넣은 값을 서버가 받는 모양으로: 하이픈·공백을 빼고 대문자로 */
export function normalizeCode(text) {
  return text.replace(/[^A-Za-z0-9]/g, '').toUpperCase()
}

export const CODE_LENGTH = 12
