import { useState } from 'react'
import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import Pagination from '../components/ui/Pagination.jsx'
import { EmptyState, ErrorState } from '../components/ui/StateView.jsx'
import InfoTabs from '../features/info/InfoTabs.jsx'
import { NOTICE_PAGE_SIZE, NOTICE_TAG, fetchNotices, noticeDate } from '../features/notices/noticesApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

/** 공지사항 목록(UI-15). 최신순으로 5개씩, 아래 숫자로 페이지를 넘긴다. 공지를 누르면 상세(/notices/번호). */
export default function NoticesPage() {
  usePageTitle('공지사항')
  const [page, setPage] = useState(0)
  const q = useApi((signal) => fetchNotices(signal, page), [page])
  const items = q.data?.items ?? []
  const totalPages = q.data ? Math.max(1, Math.ceil(q.data.totalCount / (q.data.size || NOTICE_PAGE_SIZE))) : 1

  return (
    <div className="nt">
      <PageHeader title="공지사항" tagline>
        <p className="phead__sub">서비스 소식과 점검 안내를 확인하세요</p>
        <InfoTabs group="help" />
      </PageHeader>

      <section className={`card card--hero nt__list${q.loading ? ' is-loading' : ''}`} aria-label="공지 목록" aria-busy={q.loading}>
        {q.error && <ErrorState error={q.error} onRetry={q.reload} />}
        {!q.error && !q.data && <div className="skeleton" style={{ height: 280 }} />}
        {q.data && items.length === 0 && <EmptyState title="등록된 공지가 없어요" />}
        {items.length > 0 && (
          <ul className="nt__ul">
            {items.map((n) => (
              <li key={n.noticeId}>
                <Link className="nt__row" to={`/notices/${n.noticeId}`}>
                  {NOTICE_TAG[n.noticeType] && <span className={`nt__tag nt__tag--${n.noticeType.toLowerCase()}`}>{NOTICE_TAG[n.noticeType]}</span>}
                  <span className="nt__main">
                    <b>{n.title}</b>
                    <small>{noticeDate(n.createdAt)}</small>
                  </span>
                  <Icon name="chevronRight" size={18} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={q.loading} />

      <AdSlot />
      <NoticeBox>
        <div>공지 내용은 운영 상황에 따라 바뀔 수 있어요.</div>
      </NoticeBox>
    </div>
  )
}
