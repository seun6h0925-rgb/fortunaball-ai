import { useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { MENU_GROUPS } from '../app/menu.js'
import PageHeader, { ShareLikeBar } from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import RoundNav from '../components/ui/RoundNav.jsx'
import { ErrorState } from '../components/ui/StateView.jsx'
import {
  CenterPanel,
  HotPanel,
  MakeCard,
  PreviewCard,
  SectionPanel,
  SplitPanel,
  WinterPanel,
} from '../features/stats/StatsPanels.jsx'
import {
  DEFAULT_PERIOD,
  PATTERN_PERIOD,
  TABS,
  fetchDraw,
  fetchNumbers,
  fetchPatterns,
  fetchSummary,
} from '../features/stats/statsApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

/**
 * 로또 데이터 통계 분석(UI-07, 07-2~6). 탭 6개(핫넘버·겨울잠·밸런스·하이로우·센터 존·올 구간).
 *  - 요약(API-09)은 탭마다 다시 부르지 않고 한 번에 받아서 탭이 바뀌어도 바로 보인다. 기간·기준 회차가 바뀌면 다시 받는다.
 *  - 핫넘버 그래프만 번호별 횟수(API-10)를 따로 받는다. (요약이 알려 준 회차 범위로)
 *  - [이 통계로 번호 생성]은 /generate?pattern=코드 로 이어진다. 패턴 설명과 "N회 중 M회 해당"은 API-44 값이다.
 *  - 선택한 탭은 주소(?tab=)에 담겨서 공유하거나 새로고침해도 그대로 열린다.
 * 📚 배운 내용: useSearchParams 로 주소의 값을 상태처럼 쓰기, useApi 의 deps 로 다시 불러오기
 */
export default function StatsPage() {
  usePageTitle('통계 분석')
  const [params, setParams] = useSearchParams()
  const tab = TABS.some((t) => t.key === params.get('tab')) ? params.get('tab') : 'hot'
  const [period, setPeriod] = useState(DEFAULT_PERIOD)
  const [basis, setBasis] = useState(null) // null = 가장 최근 회차

  const sq = useApi((signal) => fetchSummary(signal, period, basis), [period, basis])
  const pq = useApi((signal) => fetchPatterns(signal, PATTERN_PERIOD[period]), [period])
  const s = sq.data
  const nq = useApi(
    (signal) => (s ? fetchNumbers(signal, s.fromRound, s.toRound) : Promise.resolve(null)),
    [s?.fromRound, s?.toRound],
  )

  const dq = useApi((signal) => (basis !== null ? fetchDraw(signal, basis) : Promise.resolve(null)), [basis])
  const pattern = pq.data?.patterns?.find((p) => p.code === TABS.find((t) => t.key === tab).code)
  const latest = pq.data?.basisRound // 마지막으로 추첨이 끝난 회차
  const target = pq.data?.targetRound // 이번 주 추첨 예정 회차(사전 분석)
  const current = basis ?? target
  const pastBasis = basis !== null && latest != null && basis !== latest

  function pickTab(key) {
    setParams(key === 'hot' ? {} : { tab: key }, { replace: true })
  }

  return (
    <div className="stats">
      <PageHeader title="통계 분석" tagline share>
        <p className="phead__sub only-pc">과거 당첨번호를 다양한 기준으로 살펴봐요</p>
        <InfoTabs />
        <nav className="chip-row stats__tabs" aria-label="통계 종류">
          {TABS.map((t) => (
            <button key={t.key} type="button" className={`chip${t.key === tab ? ' is-on' : ''}`} aria-pressed={t.key === tab}
              onClick={() => pickTab(t.key)}>
              {t.label}
            </button>
          ))}
        </nav>
      </PageHeader>

      <div className="stats__top">
      <section className="card card--hero stats__bar" aria-label="분석 기준">
        <div className="stats__bar-row">
          <div className="stats__pickbox">
            <span className="stats__lbl">기준 회차</span>
            <RoundNav value={current} latest={target} latestTag="사전 분석" onChange={(r) => setBasis(r === target ? null : r)} />
          </div>
          {s && (
            <span className="chip chip--sm is-on stats__state">
              {basis === null ? `${target}회 · 사전 분석` : `${basis}회 · 확정`}
            </span>
          )}
        </div>
        {s && (
          <p className="stats__note">
            {basis === null
              ? `${target}회 사전 분석이에요. ${s.toRound}회까지의 결과로 미리 계산한 통계라서 실제 결과와 다를 수 있어요. 당첨번호가 발표되면 실제 번호로 확정되고, 통계도 다시 계산돼요.`
              : `${s.toRound}회까지의 결과로 계산한 확정 통계예요.`}
          </p>
        )}
      </section>

      {s && target && <PreviewCard tab={tab} pattern={pattern} target={target} basis={basis} draw={dq.data} />}
      </div>

      {sq.error && (
        <div className="card">
          <ErrorState error={sq.error} onRetry={sq.reload} />
        </div>
      )}
      {!sq.error && !s && <StatsSkeleton />}

      {s && (
        <div className={`stats__body${sq.loading ? ' is-loading' : ''}`} data-tab={tab} aria-busy={sq.loading}>
          {tab === 'hot' && <HotPanel s={s} nums={nq.data} period={period} setPeriod={setPeriod} />}
          {tab === 'winter' && <WinterPanel s={s} />}
          {tab === 'balance' && <SplitPanel s={s} kind="balance" period={period} setPeriod={setPeriod} />}
          {tab === 'highlow' && <SplitPanel s={s} kind="highlow" period={period} setPeriod={setPeriod} />}
          {tab === 'center' && <CenterPanel s={s} period={period} setPeriod={setPeriod} />}
          {tab === 'section' && <SectionPanel s={s} period={period} setPeriod={setPeriod} />}
        </div>
      )}

      {s && <MakeCard tab={tab} pattern={pattern} showHit={!pastBasis} />}

      <ShareLikeBar />
      <AdSlot />
      <NoticeBox>
        <div>모든 수치는 과거에 나온 결과를 정리한 사실이에요.</div>
        <div>통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.</div>
      </NoticeBox>

    </div>
  )
}

/** "당첨 정보" 묶음 화면들 사이를 오가는 칩 줄 (PC 목업에만 있다. 모바일·태블릿은 하단 바와 전체 메뉴를 쓴다) */
function InfoTabs() {
  const { pathname } = useLocation()
  const items = MENU_GROUPS.find((g) => g.key === 'info').items
  return (
    <nav className="chip-row make-tabs only-pc" aria-label="당첨 정보 메뉴">
      {items.map((it) => (
        <Link key={it.to} to={it.to} className={`chip${pathname === it.to ? ' is-on' : ''}`}
          aria-current={pathname === it.to ? 'page' : undefined}>
          {it.label}
        </Link>
      ))}
    </nav>
  )
}

function StatsSkeleton() {
  return (
    <div className="stats__body" aria-busy="true">
      {[0, 1].map((i) => (
        <div key={i} className="card card--hero spanel">
          <div className="skeleton" style={{ height: 24, width: '40%' }} />
          <div className="skeleton" style={{ height: 36 }} />
          <div className="skeleton" style={{ height: 180 }} />
        </div>
      ))}
    </div>
  )
}
