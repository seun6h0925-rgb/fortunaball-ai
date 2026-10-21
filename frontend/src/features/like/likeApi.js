import { api } from '../../api/http.js'

// 서비스 좋아요. 응답: { total(전체 수), todayCount(오늘 내가 누른 수), dailyLimit(하루 한도), remaining(남은 횟수) }
export const fetchLikes = (signal) => api.get('/api/likes', { signal })
export const pressLike = () => api.post('/api/likes')
