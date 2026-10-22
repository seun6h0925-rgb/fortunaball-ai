import { Link, useNavigate, useParams } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { ErrorState } from '../components/ui/StateView.jsx'
import InfoTabs from '../features/info/InfoTabs.jsx'
import { NOTICE_TAG, fetchNotice, noticeDate } from '../features/notices/noticesApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

/** 공지 상세(UI-15B). 없는 공지·숨긴 공지는 서버가 오류로 알려 주고, 여기서는 목록으로 돌아가는 길을 함께 보여 준다. */
export default function NoticeDetailPage() {
  usePageTitle('공지사항')
  const { id } = useParams()
  const navigate = useNavigate()
  const q = useApi((signal) => fetchNotice(signal, id), [id])
  const n = q.data

  return (
    <div className="nt">
      <PageHeader title="공지사항" tagline>
        <p className="phead__sub">서비스 소식과 점검 안내를 확인하세요</p>
        <InfoTabs group="help" />
      </PageHeader>

      <div className="nt__detail">
        <article className="card card--hero nt__article">
          {q.error ? (
            <>
              <ErrorState error={q.error} onRetry={q.reload} />
              <Link className="btn btn--ghost nt__back" to="/notices">목록으로</Link>
            </>
          ) : !n ? (
            <div className="skeleton" style={{ height: 260 }} />
          ) : (
            <>
              <div className="nt__meta">
                {NOTICE_TAG[n.noticeType] && <span className={`nt__tag nt__tag--${n.noticeType.toLowerCase()}`}>{NOTICE_TAG[n.noticeType]}</span>}
                <span>{noticeDate(n.createdAt)}</span>
              </div>
              <h2 className="nt__title">{n.title}</h2>
              <div className="nt__content">{n.content}</div>
              <button type="button" className="btn btn--ghost nt__back" onClick={() => navigate('/notices')}>목록으로</button>
            </>
          )}
        </article>
        <div className="nt__side only-pc" role="complementary" aria-label="광고">
          <span className="ad-slot__tag">광고</span>
          애드센스 광고 영역 (고정 크기)
        </div>
      </div>

      <AdSlot />
      <NoticeBox />
    </div>
  )
}
