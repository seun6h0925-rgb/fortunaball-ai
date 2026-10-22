import { Link } from 'react-router-dom'
import Icon from '../../components/ui/Icon.jsx'
import { ErrorState } from '../../components/ui/StateView.jsx'

const SHOW = 3 // 보여 줄 지역 수
const MIN_PREVIOUS = 5 // 앞 구간 1등이 이보다 적은 지역은 제외 (1건→3건 같은 우연한 변화가 1등이 되지 않게)

/**
 * 홈의 "최근 상승 지역" 카드: 최근 20회 1등 배출이 바로 앞 20회보다 많이 늘어난 지역 3곳 (API-41).
 * 모바일·태블릿은 세로 막대, PC 는 가로 막대 — 같은 마크업을 CSS 가 다르게 배치한다.
 */
export default function RegionsCard({ q }) {
  const rows = q.data ? risingRegions(q.data) : []
  const maxRate = rows.length ? rows[0].firstChangeRate : 1
  const span = q.data ? q.data.toRound - q.data.fromRound + 1 : 20

  return (
    <section className="card home-card home__regions home-regions">
      <div className="home-card__head">
        <h2 className="home-card__title">최근 상승 지역</h2>
        <Link to="/stats" className="home-card__more">
          더보기 <Icon name="chevronRight" size={14} />
        </Link>
      </div>
      <p className="home-card__cap">최근 {span}회 1등 배출 증가율</p>

      {q.loading ? (
        <div className="regions regions--busy" aria-busy="true">
          {[0.9, 0.6, 0.45].map((v, i) => (
            <div key={i} className="skeleton regions__sk" style={{ '--v': v }} />
          ))}
        </div>
      ) : q.error ? (
        <ErrorState error={q.error} onRetry={q.reload} />
      ) : rows.length === 0 ? (
        <div className="state">아직 비교할 수 있는 상승 지역이 없어요</div>
      ) : (
        <ul className="regions">
          {rows.map((r) => (
            <li key={r.sido} className="region" style={{ '--v': Math.max(0.18, r.firstChangeRate / maxRate) }}>
              <span className="region__label">{r.sido}</span>
              <span className="region__track">
                <span className="region__fill" />
              </span>
              <span className="region__pct up">▲{Math.round(r.firstChangeRate)}%</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** 증가율이 큰 순(같으면 1등 건수가 많은 순)으로 상위 지역만 고른다. 비교할 수 없으면 빈 목록 */
function risingRegions(data) {
  if (!data.previousAvailable) return []
  return data.regions
    .filter((r) => r.previousFirstCount >= MIN_PREVIOUS && r.firstChangeRate > 0)
    .sort((a, b) => b.firstChangeRate - a.firstChangeRate || b.firstCount - a.firstCount)
    .slice(0, SHOW)
}
