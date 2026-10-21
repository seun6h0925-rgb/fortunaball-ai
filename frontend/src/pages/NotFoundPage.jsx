import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import usePageTitle from '../hooks/usePageTitle.js'
import { EmptyState } from '../components/ui/StateView.jsx'

export default function NotFoundPage() {
  usePageTitle('페이지를 찾을 수 없어요')
  return (
    <>
      <PageHeader title="페이지를 찾을 수 없어요" />
      <div className="card">
        <EmptyState title="주소가 잘못되었거나 없어진 페이지예요">
          <Link to="/" className="btn btn--cta">
            홈으로 가기
          </Link>
        </EmptyState>
      </div>
    </>
  )
}
