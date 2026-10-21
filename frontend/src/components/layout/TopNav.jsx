import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import Icon from '../ui/Icon.jsx'
import Logo from '../ui/Logo.jsx'
import { MENU_GROUPS, isPathActive } from '../../app/menu.js'

// PC 상단 메뉴(1024px 이상). 메뉴 이름에 마우스를 올리거나 누르면 전체 메뉴 판이 펼쳐진다.
// 한 줄짜리 메뉴(홈·내 기록)는 바로 이동한다.
const TOP_ITEMS = [
  { key: 'home', label: '홈', to: '/', exact: true },
  { key: 'make', label: '번호 만들기', group: 'make' },
  { key: 'info', label: '당첨 정보', group: 'info' },
  { key: 'calc', label: '계산 · 가이드', group: 'calc' },
  { key: 'mine', label: '내 기록', to: '/issues' },
  { key: 'help', label: '안내 · 공지', group: 'help' },
]

function groupActive(pathname, key) {
  const g = MENU_GROUPS.find((x) => x.key === key)
  return !!g && g.items.some((i) => isPathActive(pathname, i.to))
}

export default function TopNav() {
  const { pathname } = useLocation()
  // 메뉴 판은 "어느 주소에서 열었는지"를 같이 기억한다. 다른 화면으로 이동하면 자동으로 닫힌 것으로 본다.
  const [openAt, setOpenAt] = useState(null)
  const open = openAt === pathname
  const setOpen = (value) => {
    const next = typeof value === 'function' ? value(open) : value
    setOpenAt(next ? pathname : null)
  }
  const closeTimer = useRef(null)

  // Esc 로 닫기
  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && setOpenAt(null)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const openNow = () => {
    clearTimeout(closeTimer.current)
    setOpen(true)
  }
  // 마우스가 메뉴 이름과 판 사이를 지나갈 때 깜빡 닫히지 않도록 아주 짧게 기다린다
  const closeSoon = () => {
    clearTimeout(closeTimer.current)
    closeTimer.current = setTimeout(() => setOpen(false), 120)
  }

  return (
    <div className="topnav only-pc" onMouseLeave={closeSoon} onMouseEnter={() => clearTimeout(closeTimer.current)}>
      <div className="topnav__bar">
        <div className="topnav__inner">
          <Logo />
          <nav className="topnav__links" aria-label="주요 메뉴">
            {TOP_ITEMS.map((t) =>
              t.group ? (
                <button
                  key={t.key}
                  type="button"
                  className={`topnav__link${groupActive(pathname, t.group) ? ' is-active' : ''}${open ? ' is-open' : ''}`}
                  aria-expanded={open}
                  onMouseEnter={openNow}
                  onFocus={openNow}
                  onClick={() => setOpen((o) => !o)}
                >
                  {t.label}
                  <Icon name="chevronDown" size={14} strokeWidth={2.4} />
                </button>
              ) : (
                <NavLink
                  key={t.key}
                  to={t.to}
                  end={t.exact}
                  className={({ isActive }) => `topnav__link${isActive ? ' is-active' : ''}`}
                  onMouseEnter={() => setOpen(false)}
                >
                  {t.label}
                </NavLink>
              ),
            )}
          </nav>
          <div className="topnav__right">
            <Link to="/notices" className="topnav__bell" aria-label="공지사항">
              <Icon name="bell" size={24} />
              <span className="topnav__dot" />
            </Link>
          </div>
        </div>
      </div>
      {open && (
        <>
          <div className="mega-scrim" onClick={() => setOpen(false)} aria-hidden="true" />
          <div className="mega" onMouseEnter={openNow}>
            <div className="mega__grid">
              {MENU_GROUPS.map((g) => (
                <section key={g.key}>
                  <div className="mega__head">
                    <Icon name={g.icon} size={18} />
                    {g.title}
                  </div>
                  {g.items.map((i) => (
                    <Link key={i.to} to={i.to} className={`mega__item${isPathActive(pathname, i.to) ? ' is-active' : ''}`}>
                      <span className="mega__label">{i.label}</span>
                      <span className="mega__desc">{i.desc}</span>
                    </Link>
                  ))}
                </section>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
