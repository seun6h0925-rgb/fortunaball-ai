import { Link } from 'react-router-dom'
import Icon from '../../components/ui/Icon.jsx'
import LottoBall from '../../components/ui/LottoBall.jsx'
import { ErrorState } from '../../components/ui/StateView.jsx'
import { formatDrawDate, formatEok, formatRate } from '../../lib/format.js'

/**
 * 홈 맨 위 큰 카드: 최신 회차 번호·1등 당첨금과 번호 생성 진입 버튼.
 *  - latest : 최신 회차 (API-01)    - current : 대상 회차 (API-43, 실패해도 카드는 보여 준다)
 *  - trend  : 최근 당첨금 추이 (API-11) — "전 회차 대비" 계산에만 쓴다
 */
export default function HeroCard({ latest, current, trend }) {
  if (latest.loading) return <HeroSkeleton />
  if (latest.error) {
    return (
      <section className="card card--hero home-card home__hero">
        <ErrorState error={latest.error} onRetry={latest.reload} />
      </section>
    )
  }

  const draw = latest.data
  const first = draw.prizes.find((p) => p.winningRank === 1)
  const change = prizeChange(trend.data, draw.roundNo)
  const targetRound = current.data?.targetRound

  return (
    <section className="card card--hero home-card home__hero home-hero">
      <div className="home-hero__top">
        <Link to="/draws" className="home-hero__round" aria-label={`${draw.roundNo}회차 당첨번호 보기`}>
          {draw.roundNo}회차
          <Icon name="chevronRight" size={20} />
        </Link>
        <span className="home-hero__date">{formatDrawDate(draw.drawDate)} 추첨</span>
      </div>

      <div className="home-hero__prize">
        <div>
          <div className="sub">1등 당첨금</div>
          <div className="home-hero__amount">{formatEok(first?.prizeAmount)}</div>
        </div>
        {change && (
          <div className="home-hero__change">
            <span className="sub">전 회차 대비</span>
            <span className={change.rate >= 0 ? 'up' : 'down'}>
              {change.rate >= 0 ? '▲' : '▼'} {formatRate(change.rate)}
            </span>
          </div>
        )}
      </div>

      <div className="home-hero__balls" aria-label={`당첨번호 ${draw.numbers.join(', ')} 보너스 ${draw.bonusNumber}`}>
        {draw.numbers.map((n) => (
          <LottoBall key={n} n={n} />
        ))}
        <span className="home-hero__plus" aria-hidden="true">+</span>
        <LottoBall n={draw.bonusNumber} />
      </div>

      <div className="hr" />
      <p className="home-hero__tagline">
        결과는 알 수 없어도, <b>선택은 스마트하게.</b>
      </p>

      <Link to="/generate" className="btn btn--cta btn--block">
        <Icon name="sparkle" size={20} />
        통계 번호 생성{targetRound ? ` · ${targetRound}회` : ''}
      </Link>
      <div className="home-hero__sub">
        <Link to="/analyst" className="btn btn--ghost">
          <Icon name="bars" size={18} />
          AI 애널리스트
        </Link>
        <Link to="/dream" className="btn btn--ghost">
          <Icon name="moon" size={18} />
          꿈풀이 번호
        </Link>
      </div>
    </section>
  )
}

/** 최신 회차와 바로 앞 회차의 1등 당첨금(1인당)을 비교한 증감률(%). 비교할 수 없으면 null */
function prizeChange(trendData, roundNo) {
  if (!trendData) return null
  const amountOf = (no) =>
    trendData.rounds.find((r) => r.roundNo === no)?.prizes.find((p) => p.rank === 1)?.prizeAmount
  const now = amountOf(roundNo)
  const before = amountOf(roundNo - 1)
  if (!now || !before) return null
  return { rate: ((now - before) / before) * 100 }
}

// 불러오는 동안 카드 모양을 미리 잡아 두어 화면이 밀리지 않게 한다
function HeroSkeleton() {
  return (
    <section className="card card--hero home-card home__hero home-hero" aria-busy="true">
      <div className="skeleton" style={{ height: 34, width: '55%' }} />
      <div className="skeleton" style={{ height: 56, width: '45%', marginTop: 22 }} />
      <div className="skeleton" style={{ height: 36, marginTop: 22 }} />
      <div className="skeleton" style={{ height: 56, marginTop: 40, borderRadius: 999 }} />
      <div className="skeleton" style={{ height: 48, marginTop: 12, borderRadius: 999 }} />
    </section>
  )
}
