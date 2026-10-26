import { Children, useRef, useState } from 'react'

/**
 * 홈 아래쪽 카드들(당첨금 추이 · 최근 상승 지역 · 루키 명당)을 모바일(폭 599px 이하)에서
 * 옆으로 밀어 넘기는 한 줄로 묶는다. 홈 전체를 위아래 스크롤 없이 첫 화면에 담기 위한 틀이다.
 *  - 600px 이상(태블릿·PC)에서는 CSS 가 이 틀을 "없는 것처럼" 풀어서(display: contents) 원래 격자 배치를 그대로 쓴다.
 *  - 아래 점은 지금 몇 번째 카드인지 보여 준다. 카드는 화면 폭의 88% 라서 옆 카드가 살짝 보여 밀어 볼 수 있다는 걸 알려 준다.
 */
export default function HomeRail({ children }) {
  const railRef = useRef(null)
  const [index, setIndex] = useState(0)
  const count = Children.count(children)

  function onScroll() {
    const el = railRef.current
    if (!el) return
    const max = el.scrollWidth - el.clientWidth
    if (max <= 0) return
    setIndex(Math.round((el.scrollLeft / max) * (count - 1)))
  }

  return (
    <div className="home__rail-wrap">
      <div
        className="home__rail"
        ref={railRef}
        onScroll={onScroll}
        aria-label="홈 카드 목록. 옆으로 밀어서 넘겨 보세요"
      >
        {children}
      </div>
      <div className="home__dots" aria-hidden="true">
        {Array.from({ length: count }, (_, i) => (
          <span key={i} className={`home__dot${i === index ? ' is-on' : ''}`} />
        ))}
      </div>
    </div>
  )
}
