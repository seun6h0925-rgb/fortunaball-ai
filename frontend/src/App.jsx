import { Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import FullMenuPage from './pages/FullMenuPage.jsx'
import ComingSoonPage from './pages/ComingSoonPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import HomePage from './pages/HomePage.jsx'
import GeneratePage from './pages/GeneratePage.jsx'
import ResultPage from './pages/ResultPage.jsx'
import IssuedPage from './pages/IssuedPage.jsx'
import IssuesPage from './pages/IssuesPage.jsx'
import PatternsPage from './pages/PatternsPage.jsx'
import AnalystPage from './pages/AnalystPage.jsx'
import DreamPage from './pages/DreamPage.jsx'
import StatsPage from './pages/StatsPage.jsx'
import DrawsPage from './pages/DrawsPage.jsx'
import WinnersPage from './pages/WinnersPage.jsx'
import VerifyPage from './pages/VerifyPage.jsx'
import MapPage from './pages/MapPage.jsx'
import PayoutPage from './pages/PayoutPage.jsx'
import ClaimGuidePage from './pages/ClaimGuidePage.jsx'
import AboutPage from './pages/AboutPage.jsx'
import NoticesPage from './pages/NoticesPage.jsx'
import NoticeDetailPage from './pages/NoticeDetailPage.jsx'
import AdminAuthProvider from './features/admin/AdminAuthProvider.jsx'
import AdminLayout from './components/admin/AdminLayout.jsx'
import AdminLoginPage from './pages/admin/AdminLoginPage.jsx'
import AdminDashboardPage from './pages/admin/AdminDashboardPage.jsx'
import AdminDrawsPage from './pages/admin/AdminDrawsPage.jsx'
import AdminIssuesPage from './pages/admin/AdminIssuesPage.jsx'
import AdminVerifyPage from './pages/admin/AdminVerifyPage.jsx'
import AdminRecordsPage from './pages/admin/AdminRecordsPage.jsx'
import AdminSoonPage from './pages/admin/AdminSoonPage.jsx'
import { ADMIN_MENU } from './features/admin/adminMenu.js'
import { PAGES } from './app/pages.js'

// 진짜 화면이 연결된 주소. 여기에 없는 주소는 아래에서 "준비 중" 화면으로 연결된다.
const BUILT = new Set(['/', '/generate', '/generate/patterns', '/generate/result', '/generate/issued', '/issues', '/analyst', '/dream', '/stats', '/draws', '/winners', '/verify', '/map', '/payout', '/guide/payout', '/about', '/notices', '/notices/:id'])

// 진짜 화면이 연결된 관리자 주소 (대시보드부터 하나씩 늘어난다)
const ADMIN_BUILT = new Set(['/admin', '/admin/draws', '/admin/issues', '/admin/verify', '/admin/winners'])

/**
 * 화면 주소 표. 실제 화면을 만들 때마다 이 표에서 element 만 바꿔 끼운다.
 * 아직 안 만든 화면은 PAGES(app/pages.js)의 이름으로 "준비 중" 화면을 보여준다.
 */
export default function App() {
  return (
    <Routes>
      {/* 관리자 화면: 일반 화면 틀(AppLayout) 밖에서 자기만의 틀을 쓴다 */}
      <Route path="/admin" element={<AdminAuthProvider />}>
        <Route path="login" element={<AdminLoginPage />} />
        <Route element={<AdminLayout />}>
          <Route index element={<AdminDashboardPage />} />
          <Route path="draws" element={<AdminDrawsPage />} />
          <Route path="issues" element={<AdminIssuesPage />} />
          <Route path="verify" element={<AdminVerifyPage />} />
          <Route path="winners" element={<AdminRecordsPage />} />
          {ADMIN_MENU.flatMap((g) => g.items).filter((i) => !ADMIN_BUILT.has(i.path)).map((i) => (
            <Route key={i.path} path={i.path.replace('/admin', '').replace(/^\//, '') || undefined} index={i.path === '/admin'} element={<AdminSoonPage />} />
          ))}
        </Route>
      </Route>
      <Route element={<AppLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/generate" element={<GeneratePage />} />
        <Route path="/generate/patterns" element={<PatternsPage />} />
        <Route path="/generate/result" element={<ResultPage />} />
        <Route path="/generate/issued" element={<IssuedPage />} />
        <Route path="/issues" element={<IssuesPage />} />
        <Route path="/analyst" element={<AnalystPage />} />
        <Route path="/dream" element={<DreamPage />} />
        <Route path="/stats" element={<StatsPage />} />
        <Route path="/draws" element={<DrawsPage />} />
        <Route path="/winners" element={<WinnersPage />} />
        <Route path="/verify" element={<VerifyPage />} />
        <Route path="/map" element={<MapPage />} />
        <Route path="/payout" element={<PayoutPage />} />
        <Route path="/guide/payout" element={<ClaimGuidePage />} />
        <Route path="/about" element={<AboutPage />} />
        <Route path="/notices" element={<NoticesPage />} />
        <Route path="/notices/:id" element={<NoticeDetailPage />} />
        {PAGES.filter((p) => !BUILT.has(p.path)).map((p) => (
          <Route key={p.path} path={p.path} element={<ComingSoonPage title={p.title} />} />
        ))}
        <Route path="/menu" element={<FullMenuPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
