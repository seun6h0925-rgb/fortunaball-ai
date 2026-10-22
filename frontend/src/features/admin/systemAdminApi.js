import { api } from '../../api/http.js'

export const LOG_PAGE_SIZE = 10
/** 수동 실행할 수 있는 작업 (API-40, 서버가 받는 이름 그대로) */
export const RUNNABLE_JOBS = ['DRAW_COLLECT', 'VERIFY_BATCH', 'STAT_SNAPSHOT']

/** 상태 카드용: 대시보드(API-22)를 한 번 불러오며 걸린 시간도 잰다 */
export async function fetchHealth(signal) {
  const t0 = performance.now()
  const dash = await api.get('/api/admin/dashboard', { signal })
  return { dash, ms: Math.round(performance.now() - t0) }
}

export const fetchSchedulerLogs = (signal, { jobName, page }) =>
  api.get('/api/admin/scheduler/logs', { signal, query: { jobName, page, size: LOG_PAGE_SIZE } })

export const fetchErrorLogs = (signal, { errorType, page }) =>
  api.get('/api/admin/errors', { signal, query: { errorType, page, size: LOG_PAGE_SIZE } })

export const runJob = (jobName) => api.post(`/api/admin/scheduler/${jobName}/run`)
