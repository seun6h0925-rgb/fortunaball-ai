import PageHeader from '../components/layout/PageHeader.jsx'
import usePageTitle from '../hooks/usePageTitle.js'
import { EmptyState } from '../components/ui/StateView.jsx'

// 아직 만들지 않은 화면의 임시 자리. 화면을 만들 때 App.jsx 의 Route 에서 이 화면을 빼고 진짜 화면을 연결한다.
export default function ComingSoonPage({ title }) {
  usePageTitle(title)
  return (
    <>
      <PageHeader title={title} />
      <div className="card">
        <EmptyState title="이 화면은 곧 만들어져요" />
      </div>
    </>
  )
}
