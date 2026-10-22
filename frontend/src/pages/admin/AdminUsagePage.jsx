import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import { ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { fetchUsage } from '../../features/admin/usageAdminApi.js'

const nf = new Intl.NumberFormat('ko-KR')
const PERIODS = [[1, '오늘'], [7, '7일'], [30, '30일'], [0, '전체']]
const label = (d) => (d === 1 ? '오늘' : d === 0 ? '올해 전체' : `최근 ${d}일`)

/** 증감률 문구: 직전 같은 기간 대비. 비교할 값이 없으면 null */
function delta(cur, prev) {
  if (prev == null) return null
  if (prev === 0) return cur > 0 ? { up: true, text: '▲ 신규' } : null
  const r = ((cur - prev) / prev) * 100
  return { up: r >= 0, text: `${r >= 0 ? '▲' : '▼'} ${Math.abs(r).toFixed(1)}%` }
}

function Trend({ daily }) {
  const W = 600, H = 220, L = 44, R = 12, T = 12, B = 28
  const max = Math.max(1, ...daily.map((d) => d.issues))
  const top = Math.ceil(max / 4) * 4 || 4
  const x = (i) => L + (daily.length <= 1 ? 0 : (i / (daily.length - 1)) * (W - L - R))
  const y = (v) => T + (1 - v / top) * (H - T - B)
  const pts = daily.map((d, i) => `${x(i)},${y(d.issues)}`)
  const area = daily.length ? `M${x(0)},${y(0)} L${pts.join(' L')} L${x(daily.length - 1)},${y(0)} Z` : ''
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((t) => Math.round(top * t))
  const step = Math.max(1, Math.ceil(daily.length / 6))
  return (
    <svg className="adline" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="일별 번호 발급 건수">
      {ticks.map((t) => (
        <g key={t}><line x1={L} x2={W - R} y1={y(t)} y2={y(t)} className="adline__grid" /><text x={L - 8} y={y(t) + 4} textAnchor="end" className="adline__t">{t}</text></g>
      ))}
      {area && <path d={area} className="adline__area" />}
      {daily.length > 1 && <polyline points={pts.join(' ')} className="adline__path" />}
      {daily.map((d, i) => (i % step === 0 ? <text key={d.date} x={x(i)} y={H - 8} textAnchor="middle" className="adline__t">{d.date.slice(5).replace('-', '.')}</text> : null))}
    </svg>
  )
}

export default function AdminUsagePage() {
  const [days, setDays] = useState(7)
  const { data, error, loading, reload } = useApi((signal) => fetchUsage(signal, days), [days])

  return (
    <div className="adusage">
      <header className="adhead">
        <div><h1>서비스 통계</h1><p>방문자 수는 집계하지 않아요.</p></div>
        <div className="adtabs adtabs--tight">
          {PERIODS.map(([d, l]) => <button key={d} type="button" className={`adtabs__b${days === d ? ' is-on' : ''}`} onClick={() => setDays(d)}>{l}</button>)}
        </div>
      </header>
      {error && <ErrorState error={error} onRetry={reload} />}
      {!data && loading && <Loading />}
      {data && <Body cur={data.cur} prev={data.prev} days={days} />}
    </div>
  )
}

function Body({ cur, prev, days }) {
  const feat = (code) => cur.features.find((f) => f.feature === code || f.feature?.startsWith(code))
  const analyst = feat('ANALYST')
  const dream = feat('DREAM')
  const pct = (n) => (cur.aiUseCount ? `${Math.round((n / cur.aiUseCount) * 100)}%` : '0%')
  const dIssue = delta(cur.issueCount, prev?.issueCount)
  const dAi = delta(cur.aiUseCount, prev?.aiUseCount)
  const maxPop = Math.max(1, ...cur.popular.map((p) => p.count))
  return (
    <>
      <div className="adstats">
        <div className="card adstat"><p className="adstat__label">번호 발급</p><p className="adstat__value">{nf.format(cur.issueCount)}건</p><p className="adstat__sub">{dIssue ? <span className={dIssue.up ? 'adup' : 'addown'}>{`직전 ${days}일 ${dIssue.text}`}</span> : label(days)}</p></div>
        <div className="card adstat"><p className="adstat__label">AI 이용</p><p className="adstat__value">{nf.format(cur.aiUseCount)}회</p><p className="adstat__sub">{dAi ? <span className={dAi.up ? 'adup' : 'addown'}>{dAi.text}</span> : label(days)}</p></div>
        <div className="card adstat"><p className="adstat__label">{analyst?.label || '애널리스트'}</p><p className="adstat__value">{nf.format(analyst?.success ?? 0)}회</p><p className="adstat__sub">AI 이용의 {pct(analyst?.success ?? 0)}</p></div>
        <div className="card adstat"><p className="adstat__label">{dream?.label || '꿈풀이'}</p><p className="adstat__value">{nf.format(dream?.success ?? 0)}회</p><p className="adstat__sub">AI 이용의 {pct(dream?.success ?? 0)}</p></div>
      </div>

      <div className="adrow">
        <section className="card adpanel">
          <div className="adpanel__head"><h2>일별 번호 발급 건수</h2><span>{label(days)}</span></div>
          <Trend daily={cur.daily} />
        </section>
        <section className="card adpanel">
          <div className="adpanel__head"><h2>인기 기능</h2><span>이용 횟수 순</span></div>
          {cur.popular.length === 0 ? <p className="adempty">이용 기록이 아직 없어요.</p> : cur.popular.map((p) => (
            <div key={p.rank} className="adpop"><span>{p.rank}</span><b>{p.name}</b><div className="adstat__bar"><i style={{ width: `${(p.count / maxPop) * 100}%` }} /></div><em>{nf.format(p.count)}</em></div>
          ))}
          <p className="adnote adnote--s">방문자 수는 집계하지 않아요.</p>
        </section>
      </div>
    </>
  )
}
