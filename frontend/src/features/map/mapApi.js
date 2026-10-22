import { api } from '../../api/http.js'

// 로또 명당 지도(UI-11)가 쓰는 서버 기능. 로그인 없이 누구나 쓴다.
// 판매점 한 곳(StoreItem): { storeId, name, roadAddress, jibunAddress, sido, sigungu, latitude, longitude,
//   firstAuto, firstManual, firstSemi, firstTotal, second, totalWins, recentFirst, recentSecond, rookie, distanceMeters }

/** API-12 지역·키워드 조회. 받는 값: { items, page, size, totalElements, totalPages, baseDate, statsFromRound } */
export const searchStores = (signal, { keyword, size = 20, page = 0 } = {}) =>
  api.get('/api/stores', { signal, query: { keyword: keyword?.trim(), size, page } })

/**
 * API-13 내 주변 조회 (반경 300·500·1000m, 가까운 순). 위치는 서버가 저장하지 않는다.
 * 받는 값: { items(distanceMeters 포함), radiusMeters, baseDate, statsFromRound }
 */
export const fetchNearby = (signal, { lat, lng, radius }) =>
  api.get('/api/stores/nearby', { signal, query: { lat, lng, radius } })

/** API-14 상세. 받는 값: { store, history[{roundNo, drawDate, rank, winType}], baseDate, statsFromRound } */
export const fetchStoreDetail = (signal, storeId) => api.get(`/api/stores/${storeId}`, { signal })

/** API-66 루키 명당 (서버가 한 번에 최대 10곳까지만 준다) */
export const fetchRookies = (signal, limit = 10) => api.get('/api/stores/rookies', { signal, query: { limit } })

export const RADII = [300, 500, 1000]
export const radiusLabel = (m) => (m >= 1000 ? `${m / 1000}km` : `${m}m`)

/** 거리 표시: 420m / 1.2km */
export const distanceText = (m) => (m == null ? '' : m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${m}m`)

const WIN_TYPE = { AUTO: '자동', MANUAL: '수동', SEMI_AUTO: '반자동' }
export const winTypeText = (t) => WIN_TYPE[t] ?? ''

/** 네이버 지도 앱/웹으로 열기 (주소 검색 링크라 별도 키가 필요 없다) */
export const naverMapLink = (store) =>
  `https://map.naver.com/p/search/${encodeURIComponent(`${store.name} ${store.roadAddress || store.jibunAddress || ''}`.trim())}`

/**
 * API-07 지도 AI 질문. 일반 애널리스트와 같은 서버 기능이고, context 'MAP' 과 (켜져 있으면) 위치를 함께 보낸다.
 * 위치는 서버가 저장·로그·AI 전달을 하지 않는다. 받는 값은 askAnalyst 와 같다. (answer 또는 message, remainingToday, charged ...)
 */
export const askMapAi = (question, history, me) =>
  api.post('/api/ai/analyst', { question, context: 'MAP', lat: me?.lat, lng: me?.lng, history })

/** 목록 보기 규칙: 처음엔 10곳만, 더보기를 누르면 50곳씩 페이지로 넘긴다 */
export const PREVIEW_SIZE = 10
export const PAGE_SIZE = 50
