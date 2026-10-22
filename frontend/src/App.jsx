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
import { PAGES } from './app/pages.js'

// 진짜 화면이 연결된 주소. 여기에 없는 주소는 아래에서 "준비 중" 화면으로 연결된다.
const BUILT = new Set(['/', '/generate', '/generate/patterns', '/generate/result', '/generate/issued', '/issues', '/analyst', '/dream', '/stats', '/draws', '/winners', '/verify'])

/**
 * 화면 주소 표. 실제 화면을 만들 때마다 이 표에서 element 만 바꿔 끼운다.
 * 아직 안 만든 화면은 PAGES(app/pages.js)의 이름으로 "준비 중" 화면을 보여준다.
 */
export default function App() {
  return (
    <Routes>
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
        {PAGES.filter((p) => !BUILT.has(p.path)).map((p) => (
          <Route key={p.path} path={p.path} element={<ComingSoonPage title={p.title} />} />
        ))}
        <Route path="/menu" element={<FullMenuPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
