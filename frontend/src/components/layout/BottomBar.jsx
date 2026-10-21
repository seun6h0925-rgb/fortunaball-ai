import { Link, useLocation } from 'react-router-dom'
import Icon from '../ui/Icon.jsx'
import { BOTTOM_TABS, isPathActive } from '../../app/menu.js'

// 모바일·태블릿 하단 둥근 바 (홈·생성·분석·통계·내역). 현재 주소가 속한 탭이 켜진다.
export default function BottomBar() {
  const { pathname } = useLocation()
  return (
    <nav className="bbar hide-pc" aria-label="주요 메뉴">
      {BOTTOM_TABS.map((t) => {
        const active = t.match.some((m) => isPathActive(pathname, m, t.exact))
        return (
          <Link key={t.key} to={t.to} className={`bbar__item${active ? ' is-active' : ''}`} aria-current={active ? 'page' : undefined}>
            <Icon name={t.icon} size={22} />
            {t.label}
          </Link>
        )
      })}
    </nav>
  )
}
