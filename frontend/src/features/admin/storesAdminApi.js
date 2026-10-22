import { api } from '../../api/http.js'

export const STORE_PAGE_SIZE = 30
export const UPLOAD_TYPE = 'FIRST_SECOND_CSV' // 지금 서버가 받는 파일 종류: 회차별 1·2등 당첨 판매점 CSV

/** 판매점 목록 (API-53) */
export const fetchStores = (signal, { keyword, sido, geocodeStatus, page }) =>
  api.get('/api/admin/stores', { signal, query: { keyword, sido, geocodeStatus, page, size: STORE_PAGE_SIZE } })

/** 판매점 수정 (API-31). 좌표는 위도·경도를 함께 보내야 한다. */
export const updateStore = (id, body) => api.patch(`/api/admin/stores/${id}`, body)

/** 시·도 선택 칸에 넣을 값: 공개 통계 API 에 나오는 시·도 이름을 그대로 쓴다 (판매점 목록 필터와 같은 표기) */
export const fetchSidoNames = (signal) => api.get('/api/statistics/regions', { signal }).then((r) => (r?.regions || []).map((x) => x.sido))

/** 판매점 파일 업로드 (API-30, multipart). 아직 DB 에는 반영되지 않는다. */
export function uploadStoreFile(file) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('uploadType', UPLOAD_TYPE)
  return api.post('/api/admin/stores/uploads', undefined, { formData: fd })
}
export const fetchUploads = (signal) => api.get('/api/admin/stores/uploads', { signal, query: { page: 0, size: 5 } })
export const fetchUploadDetail = (signal, id, kind) => api.get(`/api/admin/stores/uploads/${id}`, { signal, query: { kind, page: 0, size: 10 } })
export const applyUpload = (id) => api.post(`/api/admin/stores/uploads/${id}/apply`)
export const cancelUpload = (id) => api.post(`/api/admin/stores/uploads/${id}/cancel`)
