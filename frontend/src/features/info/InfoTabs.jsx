import { Link, useLocation } from 'react-router-dom'
import { MENU_GROUPS } from '../../app/menu.js'

/** "당첨 정보" 묶음 화면들 사이를 오가는 칩 줄 (PC 목업에만 있다. 모바일·태블릿은 하단 바와 전체 메뉴를 쓴다) */
export default function InfoTabs() {
  const { pathname } = useLocation()
  const items = MENU_GROUPS.find((g) => g.key === 'info').items
  return (
    <nav className="chip-row make-tabs only-pc" aria-label="당첨 정보 메뉴">
      {items.map((it) => (
        <Link key={it.to} to={it.to} className={`chip${pathname === it.to ? ' is-on' : ''}`}
          aria-current={pathname === it.to ? 'page' : undefined}>
          {it.label}
        </Link>
      ))}
    </nav>
  )
}
