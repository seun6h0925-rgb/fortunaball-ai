import { useSearchParams } from 'react-router-dom'
import PageHeader, { ShareLikeBar } from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import SideStack from '../components/ui/SideStack.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import RoundNav from '../components/ui/RoundNav.jsx'
import { ErrorState } from '../components/ui/StateView.jsx'
import { calcPayout } from '../features/draws/drawsApi.js'
import { fetchLatestDraw } from '../features/home/homeApi.js'
import InfoTabs from '../features/info/InfoTabs.jsx'
import { BasisCard, RankPicker, ResultCard } from '../features/payout/PayoutPanels.jsx'
import { RANKS } from '../features/payout/payoutRanks.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

/**
 * 당첨금·세금·실수령액(UI-12).
 *  - 회차(가장 최근이 기본)와 등수(1등이 기본)를 고르면 서버(API-15)가 세금을 계산해 준다. 참고용.
 *  - 고른 회차·등수는 주소(?round=&rank=)에 담겨서 공유하거나 새로고침해도 그대로 열린다.
 * 📚 배운 내용: useSearchParams, useApi 의 deps 로 다시 불러오기
 */
export default function PayoutPage() {
  usePageTitle('당첨금 · 세금 · 실수령액')
  const [params, setParams] = useSearchParams()
  const latestQ = useApi((signal) => fetchLatestDraw(signal), [])
  const latestNo = latestQ.data?.roundNo

  const askedRound = Number(params.get('round'))
  const round = latestNo ? (Number.isInteger(askedRound) && askedRound >= 1 && askedRound <= latestNo ? askedRound : latestNo) : null
  const askedRank = Number(params.get('rank'))
  const rank = RANKS.includes(askedRank) ? askedRank : 1

  const q = useApi((signal) => (round ? calcPayout(signal, round, rank) : Promise.resolve(null)), [round, rank])

  function change(nextRound, nextRank) {
    const next = {}
    if (nextRound !== latestNo) next.round = String(nextRound)
    if (nextRank !== 1) next.rank = String(nextRank)
    setParams(next, { replace: true })
  }

  return (
    <div className="pay">
      <PageHeader title="당첨금 · 세금 · 실수령액" tagline share>
        <p className="phead__sub">회차와 등수를 고르면 예상 실수령액을 계산해요</p>
        <InfoTabs group="calc" />
      </PageHeader>

      {latestQ.error ? (
        <div className="card"><ErrorState error={latestQ.error} onRetry={latestQ.reload} /></div>
      ) : (
        <div className="pay__grid">
          {/* 모바일에서는 래퍼를 풀고 CSS order 로 선택 → 결과 → 기준 순서를 지킨다. PC 는 왼쪽(선택·기준) / 오른쪽(결과·후원) 두 열 */}
          <div className="pay__left">
            <section className="card card--hero pay__pick" aria-label="당첨금 선택">
              <h2 className="pay__h">당첨금 선택</h2>
              <span className="pay__label">회차</span>
              <RoundNav value={round} latest={latestNo} onChange={(r) => change(r, rank)} />
              <span className="pay__label">등수</span>
              <RankPicker rank={rank} onPick={(r) => change(round, r)} />
            </section>
            <BasisCard data={q.data} />
          </div>
          <div className="pay__right">
            <ResultCard q={q} round={round} rank={rank} />
            <SideStack />
          </div>
        </div>
      )}

      <ShareLikeBar />
      <AdSlot />
      <NoticeBox>
        <div>세금은 참고용 계산이며 실제와 다를 수 있어요.</div>
        <div>당첨금은 공식 발표 기준이에요.</div>
        <div>통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.</div>
      </NoticeBox>
    </div>
  )
}
