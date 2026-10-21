import { useEffect, useRef } from 'react'
import Icon from '../../components/ui/Icon.jsx'
import { useLike } from './likeContext.js'

/**
 * 엄지(좋아요) 버튼. variant: 'compact'(제목 줄) | 'block'(공유하기 옆, 칸을 반씩 나눔)
 * 숫자는 서비스 전체 좋아요 수, 작은 글씨는 "오늘 내가 누른 횟수/하루 한도".
 */
export default function LikeButton({ variant = 'compact' }) {
  const like = useLike()
  const btn = useRef(null)

  // 누를 때마다 엄지가 통통 튀는 애니메이션을 다시 켠다
  useEffect(() => {
    const el = btn.current
    if (!el || !like.pressedAt) return
    el.classList.remove('pop')
    void el.offsetWidth
    el.classList.add('pop')
  }, [like.pressedAt])

  const full = like.loaded && like.remaining <= 0
  const count = like.loaded ? like.total.toLocaleString('ko-KR') : '–'
  const today = like.loaded ? `오늘 ${like.todayCount}/${like.dailyLimit}` : '오늘 –/5'

  return (
    <button
      ref={btn}
      type="button"
      className={`like-btn like-btn--${variant}${full ? ' is-full' : ''}`}
      onClick={like.press}
      aria-label={`좋아요 ${count}개. ${today}. 누르면 좋아요가 올라가요`}
    >
      <Icon name="thumb" size={16} color="#7CC8FC" strokeWidth={2.2} />
      {count}
      <span className="like-btn__today">{today}</span>
    </button>
  )
}
