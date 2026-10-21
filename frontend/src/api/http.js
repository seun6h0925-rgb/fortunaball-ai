import { getGuestToken } from './guest.js'

/**
 * 서버와 통신하는 단 하나의 통로. 모든 화면이 이것만 쓴다.
 *  - X-Guest-Token 을 항상 붙인다. (관리자 화면은 세션 쿠키가 자동으로 같이 간다)
 *  - 서버가 { success, data, message } / { success:false, errorCode, message, traceId } 로 주므로
 *    성공이면 data 만 돌려주고, 실패면 ApiError 를 던진다. (화면은 try/catch 한 곳에서 처리)
 *  - 변경 요청(POST/PATCH/DELETE)에는 관리자 보호 토큰(XSRF-TOKEN 쿠키)을 X-XSRF-TOKEN 헤더로 붙인다.
 */
export class ApiError extends Error {
  constructor({ status, errorCode, message, traceId }) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.traceId = traceId
  }
}

const DEFAULT_MESSAGES = {
  400: '입력값을 확인해 주세요.',
  401: '로그인이 필요해요.',
  403: '접근 권한이 없어요.',
  404: '요청한 정보를 찾을 수 없어요.',
  409: '현재 상태와 맞지 않는 요청이에요.',
  413: '더 작은 파일로 올려 주세요.',
  429: '잠시 후 다시 시도해 주세요.',
  503: '일시적으로 이용할 수 없어요. 잠시 후 다시 시도해 주세요.',
}

function readCookie(name) {
  const hit = document.cookie.split('; ').find((c) => c.startsWith(name + '='))
  return hit ? decodeURIComponent(hit.slice(name.length + 1)) : null
}

function buildUrl(path, query) {
  if (!query) return path
  const params = new URLSearchParams()
  Object.entries(query).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') params.append(k, String(v))
  })
  const qs = params.toString()
  return qs ? `${path}?${qs}` : path
}

export async function request(path, { method = 'GET', query, body, formData, signal, headers } = {}) {
  const h = { Accept: 'application/json', 'X-Guest-Token': getGuestToken(), ...headers }
  let payload
  if (formData) {
    payload = formData // multipart 는 브라우저가 Content-Type 을 직접 붙인다
  } else if (body !== undefined) {
    h['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const xsrf = readCookie('XSRF-TOKEN')
    if (xsrf) h['X-XSRF-TOKEN'] = xsrf
  }

  let res
  try {
    res = await fetch(buildUrl(path, query), { method, headers: h, body: payload, credentials: 'same-origin', signal })
  } catch (e) {
    if (e?.name === 'AbortError') throw e
    throw new ApiError({ status: 0, errorCode: 'NETWORK', message: '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.' })
  }

  let json = null
  if ((res.headers.get('content-type') || '').includes('json')) {
    try {
      json = await res.json()
    } catch {
      json = null
    }
  }
  if (!res.ok || json?.success === false) {
    throw new ApiError({
      status: res.status,
      errorCode: json?.errorCode,
      message: json?.message || DEFAULT_MESSAGES[res.status] || '일시적인 오류가 발생했어요.',
      traceId: json?.traceId,
    })
  }
  return json && 'data' in json ? json.data : json
}

export const api = {
  get: (path, opts) => request(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => request(path, { ...opts, method: 'POST', body }),
  patch: (path, body, opts) => request(path, { ...opts, method: 'PATCH', body }),
  del: (path, opts) => request(path, { ...opts, method: 'DELETE' }),
}
