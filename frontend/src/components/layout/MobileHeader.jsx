import { Link, useLocation } from 'react-router-dom'
import Icon from '../ui/Icon.jsx'
import Logo from '../ui/Logo.jsx'
import LikeButton from '../../features/like/LikeButton.jsx'

// 모바일·태블릿 맨 위 줄: 로고 + (홈에서만 알림) + 메뉴. 전체 메뉴 화면에서는 메뉴 대신 닫기(X).
export default function MobileHeader() {
  const { pathname } = useLocation()
  const inMenu = pathname === '/menu'
  return (
    <div className="mhead hide-pc">
      <Logo />
      <div className="mhead__right">
        {pathname === '/' && (
          <>
            <LikeButton variant="compact" />
            <Link to="/notices" className="icon-btn" aria-label="공지사항">
              <Icon name="bell" size={22} />
            </Link>
          </>
        )}
        {inMenu ? (
          <CloseMenu />
        ) : (
          <Link to="/menu" className="icon-btn" aria-label="전체 메뉴">
            <Icon name="menu" size={22} />
          </Link>
        )}
      </div>
    </div>
  )
}

function CloseMenu() {
  // 전체 메뉴를 열기 직전 화면으로 돌아간다. 처음부터 메뉴로 들어왔으면 홈으로 간다.
  const goBack = () => (window.history.length > 1 ? window.history.back() : (window.location.href = '/'))
  return (
    <button type="button" className="icon-btn" onClick={goBack} aria-label="메뉴 닫기">
      <Icon name="close" size={22} />
    </button>
  )
}
