import { useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../../components/ui/Icon.jsx'
import { ErrorState, Loading } from '../../components/ui/StateView.jsx'
import useApi from '../../hooks/useApi.js'
import { formatEokValue } from '../../lib/format.js'
import { fetchPrizeTrend } from './homeApi.js'
import PrizeChart from './PrizeChart.jsx'

// 기간 칸: 한 회차는 일주일이라 1개월=4회, 3개월=13회, 1년=52회로 본다. 전체는 1회차부터 최신까지.
const TABS = [
  { key: '1m', label: '1개월', rounds: 4 },
  { key: '3m', label: '3개월', rounds: 13 },
  { key: '1y', label: '1년', rounds: 52 },
  { key: 'all', label: '전체', rounds: null },
]
const CHUNK = 200 // 서버가 한 번에 주는 최대 회차 수

// 1년·전체는 다시 눌러도 또 가져오지 않도록 결과를 기억해 둔다 (회차가 바뀌면 키가 달라져 새로 가져온다)
const wideCache = new Map()

function fetchWide(tab, toRound) {
  const key = `${tab}:${toRound}`
  if (!wideCache.has(key)) {
    const ranges = []
    if (tab === '1y') {
      ranges.push([Math.max(1, toRound - 51), toRound])
    } else {
      for (let from = 1; from <= toRound; from += CHUNK) ranges.push([from, Math.min(toRound, from + CHUNK - 1)])
    }
    const promise = Promise.all(ranges.map(([from, to]) => fetchPrizeTrend(undefined, from, to)))
      .then((parts) => parts.flatMap((p) => p.rounds))
      .catch((e) => {
        wideCache.delete(key) // 실패는 기억하지 않는다 (다음에 다시 시도)
        throw e
      })
    wideCache.set(key, promise)
  }
  return wideCache.get(key)
}

/** 회차별 1등 1인당 당첨금을 그래프 점(억 단위)으로 바꾼다. 당첨금 정보가 없는 회차는 건너뛴다. */
function toPoints(rounds) {
  return rounds
    .map((r) => ({ round: r.roundNo, eok: (r.prizes.find((p) => p.rank === 1)?.prizeAmount ?? 0) / 100000000 }))
    .filter((p) => p.eok > 0)
}

/** 홈의 "역대 당첨금 추이" 카드. trend 는 홈이 미리 가져온 최근 20회(API-11)다. */
export default function PrizeTrendCard({ trend }) {
  const [tab, setTab] = useState('3m')
  const wide = tab === '1y' || tab === 'all'
  const toRound = trend.data?.toRound ?? 0
  const wideQuery = useApi(() => (wide && toRound ? fetchWide(tab, toRound) : Promise.resolve(null)), [wide ? tab : '', toRound])

  let points = []
  let busy = false
  let error = null
  if (trend.loading) {
    busy = true
  } else if (trend.error) {
    error = { error: trend.error, retry: trend.reload }
  } else if (wide) {
    if (wideQuery.loading) busy = true
    else if (wideQuery.error) error = { error: wideQuery.error, retry: wideQuery.reload }
    else points = toPoints(wideQuery.data ?? [])
  } else {
    const count = TABS.find((t) => t.key === tab).rounds
    points = toPoints(trend.data.rounds.slice(-count))
  }

  const stat = summarize(points)

  return (
    <section className="card home-card home__trend home-trend">
      <div className="home-card__head">
        <h2 className="home-card__title">역대 당첨금 추이</h2>
        <Link to="/stats" className="home-card__more">
          더보기 <Icon name="chevronRight" size={14} />
        </Link>
      </div>

      <div className="home-trend__tabs chip-row" role="tablist" aria-label="기간 선택">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`chip chip--sm${tab === t.key ? ' is-on' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="home-trend__stats">
        {stat ? (
          <>
            <span>
              최고 <b>{formatEokValue(stat.max)}</b>
            </span>
            <span>
              평균 <b>{formatEokValue(stat.avg)}</b>
            </span>
            <span>
              최근{' '}
              {stat.dir !== 0 && <span className={stat.dir > 0 ? 'up' : 'down'}>{stat.dir > 0 ? '▲' : '▼'} </span>}
              <b>{formatEokValue(stat.last)}</b>
            </span>
          </>
        ) : (
          <span>&nbsp;</span>
        )}
      </div>

      {error ? (
        <ErrorState error={error.error} onRetry={error.retry} />
      ) : busy ? (
        <div className="chart chart--busy">
          <Loading label="그래프를 불러오는 중이에요" />
        </div>
      ) : points.length === 0 ? (
        <div className="chart chart--busy">
          <div className="state">표시할 당첨금 정보가 아직 없어요</div>
        </div>
      ) : (
        <PrizeChart points={points} />
      )}
    </section>
  )
}

function summarize(points) {
  if (points.length === 0) return null
  const values = points.map((p) => p.eok)
  const last = values[values.length - 1]
  const prev = values.length > 1 ? values[values.length - 2] : last
  return {
    max: Math.max(...values),
    avg: values.reduce((a, b) => a + b, 0) / values.length,
    last,
    dir: Math.sign(last - prev),
  }
}
