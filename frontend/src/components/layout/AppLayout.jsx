import { Outlet, useLocation } from 'react-router-dom'
import TopNav from './TopNav.jsx'
import MobileHeader from './MobileHeader.jsx'
import BottomBar from './BottomBar.jsx'
import ScrollToTop from './ScrollToTop.jsx'

// 모든 사용자 화면의 공통 틀: PC 는 상단 메뉴, 모바일·태블릿은 로고 줄 + 하단 바.
// 본문(page__inner)은 주소가 바뀔 때마다 새로 그려져 부드러운 전환 애니메이션이 한 번 재생된다.
export default function AppLayout() {
  const { pathname } = useLocation()
  return (
    <div className="app">
      <ScrollToTop />
      <TopNav />
      <MobileHeader />
      <main className="page">
        <div className="page__inner" key={pathname}>
          <Outlet />
        </div>
      </main>
      <BottomBar />
    </div>
  )
}
