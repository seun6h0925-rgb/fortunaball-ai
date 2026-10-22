import { request } from '../../api/http.js'
import { normalizeCode, CODE_LENGTH } from '../../lib/issueCode.js'

// 익명 당첨 인증 등록(UI-10)이 쓰는 서버 기능. 로그인 없이 쓴다.

export const COMMENT_MAX = 300
export const PHOTO_MAX_BYTES = 10 * 1024 * 1024
export { CODE_LENGTH, normalizeCode }

/**
 * API-16 익명 당첨 인증 등록 (multipart/form-data)
 *  photo 사진 1장(JPG/PNG, 10MB 이하) / issueCode 발급코드 / consent 저장·검수 동의 / comment 한마디(선택, 300자)
 * 받는 값: { verificationId, reviewStatus('PENDING'), message }
 * ※ 회차와 등수는 보내지 않는다. 서버가 용지 QR 과 당첨번호를 비교해서 정한다.
 */
export function registerProof(signal, { photo, issueCode, comment }) {
  const fd = new FormData()
  fd.append('photo', photo)
  fd.append('issueCode', normalizeCode(issueCode))
  fd.append('consent', 'true')
  if (comment.trim()) fd.append('comment', comment.trim())
  return request('/api/verifications', { method: 'POST', formData: fd, signal })
}

/** 올리기 전에 브라우저에서 먼저 걸러 보는 사진 검사. 문제가 없으면 '' */
export function checkPhoto(file) {
  if (!file) return '사진을 선택해 주세요.'
  if (!['image/jpeg', 'image/png'].includes(file.type)) return 'JPG 또는 PNG 사진만 올릴 수 있어요.'
  if (file.size > PHOTO_MAX_BYTES) return '사진은 10MB 이하만 올릴 수 있어요.'
  return ''
}
