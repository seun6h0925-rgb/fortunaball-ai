import { api } from '../../api/http.js'

export const PHOTO_PAGE_SIZE = 20

/** 인증 목록 (API-28). status 가 비면 전체 */
export const fetchVerifications = (signal, { status, page }) =>
  api.get('/api/admin/verifications', { signal, query: { status, page, size: PHOTO_PAGE_SIZE } })

/** 검수 대기 건수만 필요할 때 */
export const fetchPendingCount = (signal) =>
  api.get('/api/admin/verifications', { signal, query: { status: 'PENDING', page: 0, size: 1 } }).then((r) => r.totalElements)

/** 검수 처리 (API-29). reviewStatus: APPROVED | REJECTED | HIDDEN. 반려·숨김은 코멘트(사유)가 꼭 필요하다. */
export const reviewVerification = (id, reviewStatus, adminComment) =>
  api.patch(`/api/admin/verifications/${id}`, { reviewStatus, adminComment })

/** 인증 사진 주소 (API-62). 로그인 쿠키로 열리는 관리자 전용 주소라 <img src> 에 그대로 쓴다. */
export const photoSrc = (id) => `/api/admin/verifications/${id}/photo`
