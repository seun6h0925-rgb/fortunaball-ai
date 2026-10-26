import { api } from '../../api/http.js'

export const SCOPES = ['ALL', 'ANALYST', 'DREAM']
export const MATCH = { CONTAINS: '포함', EXACT: '정확히' }

/** 정책 전체 목록 (API-54). 정책은 수십 개 이하라 한 번에 받고 화면에서 거른다. */
export const fetchPolicies = (signal) => api.get('/api/admin/ai/policies', { signal })
export const createPolicy = (body) => api.post('/api/admin/ai/policies', body)
export const updatePolicy = (id, body) => api.patch(`/api/admin/ai/policies/${id}`, body)
