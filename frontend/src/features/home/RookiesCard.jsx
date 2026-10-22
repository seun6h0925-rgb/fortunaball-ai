import { Link } from 'react-router-dom'
import Icon from '../../components/ui/Icon.jsx'
import { ErrorState } from '../../components/ui/StateView.jsx'

/** 홈의 "루키 명당" 카드: 최근 1년 1·2등 당첨이 많은 판매점 (API-66). 각 줄을 누르면 명당 지도로 간다. */
export default function RookiesCard({ q }) {
  const items = q.data?.items ?? []
  const months = q.data?.periodMonths ?? 12

  return (
    <section className="card home-card home__rookies home-rookies">
      <div className="home-card__head">
        <h2 className="home-card__title">루키 명당</h2>
        <Link to="/map" className="home-card__more">
          더보기 <Icon name="chevronRight" size={14} />
        </Link>
      </div>
      <p className="home-card__cap">최근 {months === 12 ? '1년' : `${months}개월`} 1·2등 당첨 횟수</p>

      {q.loading ? (
        <ul className="rookies" aria-busy="true">
          {[0, 1, 2, 3].map((i) => (
            <li key={i} className="rookie">
              <div className="skeleton" style={{ height: 34, width: '100%' }} />
            </li>
          ))}
        </ul>
      ) : q.error ? (
        <ErrorState error={q.error} onRetry={q.reload} />
      ) : items.length === 0 ? (
        <div className="state">아직 루키 명당이 없어요</div>
      ) : (
        <ul className="rookies">
          {items.map((s, i) => (
            <li key={s.storeId}>
              <Link to="/map" className="rookie">
                <span className="rookie__rank">{i + 1}</span>
                <span className="rookie__text">
                  <span className="rookie__name">{s.name}</span>
                  <span className="rookie__area">
                    {s.sido} {s.sigungu}
                  </span>
                </span>
                <span className="rookie__count">{s.recentFirst + s.recentSecond}회</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
