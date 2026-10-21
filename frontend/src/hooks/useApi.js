import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * 화면이 열릴 때 서버에서 데이터를 가져오는 공통 훅.
 *   const { data, error, loading, reload } = useApi((signal) => api.get('/api/draws/latest', { signal }), [])
 * 두 번째 인자(deps: 숫자·글자 값들)가 바뀌면 다시 가져오고, 화면을 떠나면 진행 중인 요청을 취소한다.
 * loading 은 "지금 요청과 받아 둔 결과가 짝이 아닐 때"로 계산한다. (이펙트 안에서 상태를 바로 바꾸지 않기 위함)
 */
export default function useApi(fetcher, deps = []) {
  const [tick, setTick] = useState(0)
  const key = `${JSON.stringify(deps)}#${tick}`
  const [result, setResult] = useState({ key: null, data: null, error: null })

  const fetcherRef = useRef(fetcher)
  useEffect(() => {
    fetcherRef.current = fetcher
  })

  useEffect(() => {
    const ctrl = new AbortController()
    fetcherRef
      .current(ctrl.signal)
      .then((data) => setResult({ key, data, error: null }))
      .catch((error) => {
        if (error?.name === 'AbortError') return
        setResult({ key, data: null, error })
      })
    return () => ctrl.abort()
  }, [key])

  const loading = result.key !== key
  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { data: result.data, error: loading ? null : result.error, loading, reload }
}
