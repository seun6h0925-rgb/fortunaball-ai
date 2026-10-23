import { useState } from 'react'
import { Link } from 'react-router-dom'
import PageHeader, { ShareLikeBar } from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import Pagination from '../components/ui/Pagination.jsx'
import { EmptyState, ErrorState } from '../components/ui/StateView.jsx'
import InfoTabs from '../features/info/InfoTabs.jsx'
import { CodeLookup, CommentList, RecordRow, SummaryTiles } from '../features/winners/WinnersPanels.jsx'
import { RANK_FILTERS, fetchRecords } from '../features/winners/winnersApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

const PAGE_SIZE = 10

/**
 * 우리 서비스 당첨 기록(UI-09). 이 서비스에서 발급한 번호 중 공개된 당첨 기록(API-17).
 *  - 위쪽 요약: 누적 건수·3등 이상 건수·최근 회차는 서버가 준 건수/목록에서 읽는다.
 *  - 공개 기록은 회차·등수·일치 개수만 보인다. (발급 번호·발급코드·사진은 서버가 내려주지 않는다)
 *  - 내 발급코드로는 아래 "발급코드로 상세 조회"에서 내 번호의 결과를 볼 수 있다.
 * 📚 배운 내용: useApi 의 deps 로 필터·페이지가 바뀔 때 다시 불러오기
 */
export default function WinnersPage() {
  usePageTitle('우리 서비스 당첨 기록')
  const [rank, setRank] = useState(null)
  const [page, setPage] = useState(0)

  const list = useApi((signal) => fetchRecords(signal, { rank, page, size: PAGE_SIZE }), [rank, page])
  // 요약용: 전체 중 앞쪽 한마디가 있는 기록과 건수. 3등 이상은 등수별 건수를 더한다.
  const recent = useApi((signal) => fetchRecords(signal, { size: 50 }), [])
  const r1 = useApi((signal) => fetchRecords(signal, { rank: 1, size: 1 }), [])
  const r2 = useApi((signal) => fetchRecords(signal, { rank: 2, size: 1 }), [])
  const r3 = useApi((signal) => fetchRecords(signal, { rank: 3, size: 1 }), [])
  const topCount = r1.data && r2.data && r3.data ? r1.data.totalElements + r2.data.totalElements + r3.data.totalElements : null

  const first = list.data
  const items = first ? first.items : []
  const comments = (recent.data?.items ?? []).filter((it) => it.comment).slice(0, 5)

  function pickRank(next) {
    setRank(next)
    setPage(0)
  }

  return (
    <div className="win">
      <PageHeader title="우리 서비스 당첨 기록" tagline share>
        <p className="phead__sub only-pc">이 서비스에서 발급된 번호의 공개 당첨 결과예요</p>
        <InfoTabs />
      </PageHeader>

      <SummaryTiles total={recent.data?.totalElements} topCount={topCount} latestRound={recent.data?.items?.[0]?.roundNo} />

      <div className="win__grid">
        <div className="win__main">
          <CodeLookup />

          <section className="card card--hero win__records" aria-label="공개된 당첨 기록">
            <h2 className="win__title">공개된 당첨 기록</h2>
            <nav className="chip-row" aria-label="등수">
              {RANK_FILTERS.map((r) => (
                <button key={r ?? 'all'} type="button" className={`chip chip--sm${r === rank ? ' is-on' : ''}`}
                  aria-pressed={r === rank} onClick={() => pickRank(r)}>
                  {r ? `${r}등` : '전체'}
                </button>
              ))}
            </nav>

            {list.loading && !first && <div className="skeleton" style={{ height: 160 }} aria-busy="true" />}
            {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
            {first && items.length === 0 && (
              <EmptyState title="아직 공개된 당첨 기록이 없어요">
                <p>{rank ? `${rank}등 기록이 아직 없어요.` : '당첨 기록이 쌓이면 여기에 보여요.'}</p>
              </EmptyState>
            )}
            {items.length > 0 && (
              <ul className={`win__list${list.loading ? ' is-loading' : ''}`}>
                {items.map((it) => (
                  <RecordRow key={it.recordId} item={it} />
                ))}
              </ul>
            )}
            {first && first.totalPages > 1 && (
              <Pagination page={page} totalPages={first.totalPages} onChange={setPage} disabled={list.loading} />
            )}
            <p className="win__note">발급코드와 발급 시각은 그대로 공개해요. 게스트는 앞 4자리만 보이고, 번호는 발급코드로 조회할 수 있어요.</p>
          </section>
        </div>

        <div className="win__side">
          <CommentList items={comments} />
          <Link to="/verify" className="btn btn--cta btn--block">
            익명 당첨 인증 등록하기
          </Link>
        </div>
      </div>

      <ShareLikeBar />
      <AdSlot />
      <NoticeBox>
        <div>발급번호와 공식 당첨번호를 비교한 과거 기록이에요.</div>
        <div>공개 기록에는 개인정보가 없어요.</div>
        <div>통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.</div>
      </NoticeBox>
    </div>
  )
}
