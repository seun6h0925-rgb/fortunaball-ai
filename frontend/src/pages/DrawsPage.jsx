import { useSearchParams } from 'react-router-dom'
import PageHeader, { ShareLikeBar } from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import SideStack from '../components/ui/SideStack.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { ErrorState } from '../components/ui/StateView.jsx'
import { PrizeTable, RoundCard } from '../features/draws/DrawsPanels.jsx'
import { fetchDraw } from '../features/draws/drawsApi.js'
import { TicketCard } from '../features/ticket/TicketCheck.jsx'
import { fetchLatestDraw } from '../features/home/homeApi.js'
import InfoTabs from '../features/info/InfoTabs.jsx'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

/**
 * 회차별 당첨번호(UI-08).
 *  - 처음에는 가장 최근 회차(API-01). ‹ › 와 선택 창(회차 입력·날짜·목록)으로 다른 회차(API-02)를 본다.
 *  - 고른 회차는 주소(?round=)에 담겨서 공유하거나 새로고침해도 그대로 열린다.
 *  - 1~3등은 눌러서 세금·실수령액(API-15)을 본다. (참고용)
 * 📚 배운 내용: useSearchParams, useApi 의 deps 로 다시 불러오기
 */
export default function DrawsPage() {
  usePageTitle('회차별 당첨번호')
  const [params, setParams] = useSearchParams()
  const latestQ = useApi((signal) => fetchLatestDraw(signal), [])
  const latestNo = latestQ.data?.roundNo

  const asked = Number(params.get('round'))
  const chosen = latestNo ? (Number.isInteger(asked) && asked >= 1 && asked <= latestNo ? asked : latestNo) : null

  // 최신 회차는 이미 받은 값을 쓰고, 다른 회차만 새로 부른다
  const drawQ = useApi(
    (signal) => {
      if (!chosen) return Promise.resolve(null)
      if (chosen === latestNo) return Promise.resolve(latestQ.data)
      return fetchDraw(signal, chosen)
    },
    [chosen, latestNo],
  )
  const draw = drawQ.data && drawQ.data.roundNo === chosen ? drawQ.data : null

  function pick(round) {
    setParams(round === latestNo ? {} : { round: String(round) }, { replace: true })
  }

  const error = latestQ.error || drawQ.error
  return (
    <div className="draws">
      <PageHeader title="회차별 당첨번호" tagline share>
        <p className="phead__sub only-pc">회차를 넘기거나 선택해서 당첨 결과를 확인해요</p>
        <InfoTabs />
      </PageHeader>

      {error ? (
        <div className="card">
          <ErrorState error={error} onRetry={() => { latestQ.reload(); drawQ.reload() }} />
        </div>
      ) : (
        <div className={`draws__body${drawQ.loading ? ' is-loading' : ''}`} aria-busy={drawQ.loading}>
          <div className="draws__left">
            <RoundCard draw={draw} latest={latestNo} onPick={pick} />
            <TicketCard />
            <SideStack />
          </div>
          {draw ? <PrizeTable key={draw.roundNo} draw={draw} /> : <div className="card card--hero draws__prizes"><div className="skeleton" style={{ height: 260 }} /></div>}
        </div>
      )}

      <ShareLikeBar />
      <AdSlot />
      <NoticeBox>
        <div>당첨금과 당첨자 수는 공식 발표 기준이에요.</div>
        <div>통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.</div>
      </NoticeBox>
    </div>
  )
}
