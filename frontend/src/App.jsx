import { Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import FullMenuPage from './pages/FullMenuPage.jsx'
import ComingSoonPage from './pages/ComingSoonPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import HomePage from './pages/HomePage.jsx'
import GeneratePage from './pages/GeneratePage.jsx'
import { PAGES } from './app/pages.js'

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
        {PAGES.filter((p) => p.path !== '/' && p.path !== '/generate').map((p) => (
          <Route key={p.path} path={p.path} element={<ComingSoonPage title={p.title} />} />
        ))}
        <Route path="/menu" element={<FullMenuPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
