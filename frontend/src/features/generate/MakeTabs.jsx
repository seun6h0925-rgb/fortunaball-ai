import { Link, useLocation } from 'react-router-dom'
import { MENU_GROUPS } from '../../app/menu.js'

// '통계 번호 생성' 흐름에 속하는 주소들. 이 화면들에서는 첫 번째 칩이 켜져 있다.
const FLOW = ['/generate', '/generate/result', '/generate/issued']

/**
 * "번호 만들기" 화면들 사이를 오가는 칩 줄 (PC 목업에만 있다. 모바일·태블릿은 하단 바와 전체 메뉴를 쓴다).
 * 항목은 메뉴 원본(app/menu.js 의 '번호 만들기' 묶음)을 그대로 쓰므로 이름·순서가 메뉴와 항상 같다.
 */
export default function MakeTabs() {
  const { pathname } = useLocation()
  const items = MENU_GROUPS.find((g) => g.key === 'make').items
  return (
    <nav className="chip-row make-tabs only-pc" aria-label="번호 만들기 메뉴">
      {items.map((it) => {
        const on = it.to === '/generate' ? FLOW.includes(pathname) : pathname === it.to
        return (
          <Link key={it.to} to={it.to} className={`chip${on ? ' is-on' : ''}`} aria-current={on ? 'page' : undefined}>
            {it.label}
          </Link>
        )
      })}
    </nav>
  )
}
