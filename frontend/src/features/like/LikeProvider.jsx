import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { LikeContext } from './likeContext.js'
import { fetchLikes, pressLike } from './likeApi.js'
import { useToast } from '../toast/toastContext.js'

const LIMIT_MESSAGE = '오늘은 좋아요를 모두 눌렀어요. 내일 또 눌러 주세요!'

export default function LikeProvider({ children }) {
  const toast = useToast()
  const [state, setState] = useState({ loaded: false, total: 0, todayCount: 0, dailyLimit: 5, remaining: 5 })
  const [pressedAt, setPressedAt] = useState(0) // 누를 때마다 바뀌어 버튼 애니메이션을 다시 켠다
  const busy = useRef(false)

  useEffect(() => {
    const ctrl = new AbortController()
    fetchLikes(ctrl.signal)
      .then((d) => setState({ loaded: true, ...d }))
      .catch(() => {
        /* 좋아요 숫자를 못 가져와도 다른 기능에는 영향이 없다. 숫자만 비워 둔다 */
      })
    return () => ctrl.abort()
  }, [])

  const press = useCallback(async () => {
    if (busy.current) return
    if (state.loaded && state.remaining <= 0) {
      toast(LIMIT_MESSAGE) // 한도에 닿았으면 서버에 묻지 않고 바로 안내
      return
    }
    busy.current = true
    try {
      const d = await pressLike()
      setState({ loaded: true, ...d })
      setPressedAt(Date.now())
    } catch (e) {
      if (e.status === 429) setState((s) => ({ ...s, remaining: 0, todayCount: s.dailyLimit }))
      toast(e.message || '좋아요를 누르지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      busy.current = false
    }
  }, [state.loaded, state.remaining, toast])

  const value = useMemo(() => ({ ...state, pressedAt, press }), [state, pressedAt, press])
  return <LikeContext.Provider value={value}>{children}</LikeContext.Provider>
}
