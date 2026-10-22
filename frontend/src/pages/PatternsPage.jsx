import { useState } from 'react'
import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import LottoBall from '../components/ui/LottoBall.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { EmptyState, ErrorState } from '../components/ui/StateView.jsx'
import MakeTabs from '../features/generate/MakeTabs.jsx'
import { DEFAULT_PERIOD, PERIODS, fetchPatterns } from '../features/patterns/patternsApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

/**
 * 패턴 모아보기(UI-02B).
 * 기간(최근 20·50·100회)을 고르면 패턴 6개가 "그 기간 중 몇 회 해당했는지"와 함께 나온다.
 * 카드의 [이 패턴으로 번호 생성]을 누르면 /generate?pattern=코드 로 가서 그 패턴으로 번호를 만든다.
 *  - 기준 회차·통계 상태는 서버가 정한 값을 그대로 보여 준다. (회차를 고르는 기능은 서버에 아직 없다)
 * 📚 배운 내용: useApi 의 deps 로 기간이 바뀔 때 다시 불러오기, Link 로 화면 이동
 */
export default function PatternsPage() {
  usePageTitle('패턴 모아보기')
  const [period, setPeriod] = useState(DEFAULT_PERIOD)
  const q = useApi((signal) => fetchPatterns(signal, period), [period])

  const d = q.data
  return (
    <div className="pat">
      <PageHeader title="패턴 모아보기" tagline>
        <p className="phead__sub only-pc">회차별 통계 패턴을 비교하고, 마음에 드는 패턴으로 번호를 만들어요</p>
        <p className="phead__sub hide-pc">회차별 통계 패턴을 비교하고 골라 만들어요</p>
        <MakeTabs />
      </PageHeader>

      <section className="card card--hero pat__bar" aria-label="분석 기준">
        <div className="pat__bar-row">
          {d && (
            <span className="chip chip--sm is-on" aria-label="통계 상태">
              {d.targetRound}회 · {d.statusLabel}
            </span>
          )}
          <div className="pat__periods" role="group" aria-label="기간">
            <span className="sub">기간</span>
            {PERIODS.map((p) => (
              <button
                key={p}
                type="button"
                className={`chip chip--sm${p === period ? ' is-on' : ''}`}
                aria-pressed={p === period}
                onClick={() => setPeriod(p)}
              >
                최근 {p}회
              </button>
            ))}
          </div>
        </div>
        {d && (
          <p className="pat__note">
            분석은 {d.basisRound}회까지의 결과예요. 토요일 추첨 결과가 올라오면 통계가 새로 만들어져요.
          </p>
        )}
      </section>

      {q.error && (
        <div className="card">
          <ErrorState error={q.error} onRetry={q.reload} />
        </div>
      )}

      {!q.error && !d && <PatternSkeleton />}

      {d && d.patterns.length === 0 && (
        <div className="card">
          <EmptyState title="아직 보여 줄 패턴이 없어요">통계가 준비되면 이곳에 나타나요.</EmptyState>
        </div>
      )}

      {d && d.patterns.length > 0 && (
        <div className={`pat__grid${q.loading ? ' is-loading' : ''}`} aria-busy={q.loading}>
          {d.patterns.map((p) => (
            <PatternCard key={p.code} p={p} period={d.period} />
          ))}
        </div>
      )}

      <AdSlot />
      <NoticeBox>
        <div>구성 예시는 해당 패턴 조건으로 만든 번호 한 게임이에요.</div>
        <div>모든 수치는 과거에 나온 결과를 정리한 사실이에요.</div>
        <div>통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.</div>
      </NoticeBox>
    </div>
  )
}

function PatternCard({ p, period }) {
  // 서버가 이 패턴으로 실제 생성한 번호. 못 받았으면 예시 칸을 통째로 감춘다.
  const balls = p.exampleNumbers ?? []
  return (
    <article className="card card--hero pcard">
      <div className="pcard__head">
        <h2 className="pcard__name">{p.name}</h2>
        <span className="pcard__badge">
          <span className="hide-pc">최근 {period}회 중 </span>
          {p.hitCount}회 해당
        </span>
      </div>
      <p className="pcard__desc">{p.description}</p>
      {balls.length > 0 && (
        <div className="pcard__sample">
          <span className="sub">구성 예시</span>
          <div className="pcard__balls" aria-label={`구성 예시 ${balls.join(', ')}`}>
            {balls.map((n) => (
              <LottoBall key={n} n={n} />
            ))}
          </div>
          <span className="sub only-pc">최근 {period}회 기준</span>
        </div>
      )}
      <Link to={`/generate?pattern=${p.code}`} className="btn btn--cta btn--block">
        이 패턴으로 번호 생성
      </Link>
    </article>
  )
}

function PatternSkeleton() {
  return (
    <div className="pat__grid" aria-busy="true">
      {[0, 1, 2].map((i) => (
        <div key={i} className="card card--hero pcard">
          <div className="skeleton" style={{ height: 24, width: '50%' }} />
          <div className="skeleton" style={{ height: 18 }} />
          <div className="skeleton" style={{ height: 40, marginTop: 10 }} />
          <div className="skeleton" style={{ height: 52 }} />
        </div>
      ))}
    </div>
  )
}
