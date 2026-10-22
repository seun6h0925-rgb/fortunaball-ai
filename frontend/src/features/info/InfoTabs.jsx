import { Link, useLocation } from 'react-router-dom'
import { MENU_GROUPS } from '../../app/menu.js'

/**
 * 같은 묶음의 화면들 사이를 오가는 칩 줄 (PC 목업에만 있다. 모바일·태블릿은 하단 바와 전체 메뉴를 쓴다)
 *  group: 'info'(당첨 정보, 기본) | 'calc'(계산·가이드) | 'help'(안내·공지)
 */
export default function InfoTabs({ group = 'info' }) {
  const { pathname } = useLocation()
  const g = MENU_GROUPS.find((x) => x.key === group)
  return (
    <nav className="chip-row make-tabs only-pc" aria-label={`${g.title} 메뉴`}>
      {g.items.map((it) => {
        const on = pathname === it.to || (it.to === '/notices' && pathname.startsWith('/notices/'))
        return (
          <Link key={it.to} to={it.to} className={`chip${on ? ' is-on' : ''}`} aria-current={on ? 'page' : undefined}>
            {it.mLabel && group === 'calc' ? it.mLabel : it.label}
          </Link>
        )
      })}
    </nav>
  )
}
