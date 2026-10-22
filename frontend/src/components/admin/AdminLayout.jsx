import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import Icon from '../ui/Icon.jsx'
import { useAdminAuth } from '../../features/admin/adminAuthContext.js'
import { ADMIN_MENU } from '../../features/admin/adminMenu.js'

/** 로그인한 관리자만 들어오는 공통 틀: 왼쪽 메뉴 + 오른쪽 본문 */
export default function AdminLayout() {
  const { status, admin, logout } = useAdminAuth()
  const location = useLocation()
  const navigate = useNavigate()

  if (status === 'loading') return <div className="adshell adshell--wait">확인하는 중…</div>
  if (status === 'out') return <Navigate to="/admin/login" replace state={{ from: location.pathname }} />

  const onLogout = async () => {
    await logout()
    navigate('/admin/login', { replace: true })
  }

  return (
    <div className="adshell">
      <aside className="adside">
        <div className="adside__brand">
          포르투나볼 AI <span className="adside__tag">관리자</span>
        </div>
        <nav className="adside__nav" aria-label="관리자 메뉴">
          {ADMIN_MENU.map((g) => (
            <div key={g.group} className="adside__group">
              <p className="adside__title">{g.group}</p>
              {g.items.map((it) => (
                <NavLink key={it.path} to={it.path} end={it.path === '/admin'} className={({ isActive }) => `adside__item${isActive ? ' is-active' : ''}`}>
                  <Icon name={it.icon} size={18} />
                  {it.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="adside__me">
          <b>{admin?.loginId}</b>
          <button type="button" className="adside__logout" onClick={onLogout}>로그아웃</button>
        </div>
      </aside>
      <main className="admain">
        <Outlet />
      </main>
    </div>
  )
}
