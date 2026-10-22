import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import { ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { fetchAiOps } from '../../features/admin/aiAdminApi.js'

const nf = new Intl.NumberFormat('ko-KR')
const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const usd = (v) => `$${Number(v ?? 0).toFixed(2)}`
const PERIODS = [[1, '오늘'], [7, '7일'], [30, '30일']]
const FEATURE = { ANALYST: '애널리스트', DREAM: '꿈풀이' }
const POLICY = {
  OFF_TOPIC: '범위 밖 질문', GUARANTEE_REQUEST: '당첨 보장 요구', DESPERATE_REQUEST: '절박한 요구',
  PURCHASE_PUSH: '구매 권유 요구', LOCATION_REQUIRED: '위치 필요 질문', DREAM_LOW_RELEVANCE: '꿈 연결 상징 부족',
}

function Meter({ label, text, pct }) {
  return (
    <div className="admeter">
      <p><span>{label}</span><b>{text}</b></p>
      <div className="adstat__bar"><i style={{ width: `${Math.min(100, pct)}%` }} /></div>
    </div>
  )
}

export default function AdminAiPage() {
  const [days, setDays] = useState(1)
  const { data, error, loading, reload } = useApi((signal) => fetchAiOps(signal, days), [days])

  return (
    <div className="adai">
      <header className="adhead">
        <div>
          <h1>AI 운영 관리</h1>
          <p>대화·꿈 원문은 저장하지 않아요. 건수와 상태만 보여요.</p>
        </div>
        <div className="adtabs adtabs--tight">
          {PERIODS.map(([d, label]) => <button key={d} type="button" className={`adtabs__b${days === d ? ' is-on' : ''}`} onClick={() => setDays(d)}>{label}</button>)}
        </div>
      </header>

      {error && <ErrorState error={error} onRetry={reload} />}
      {!data && loading && <Loading />}
      {data && <Body d={data} days={days} />}
    </div>
  )
}

function Body({ d, days }) {
  const { summary, limit, logs } = d.all
  const times = logs.items.map((i) => i.responseTimeMs).filter((t) => t != null)
  const avg = times.length ? (times.reduce((a, b) => a + b, 0) / times.length / 1000).toFixed(1) : null
  const period = days === 1 ? '오늘' : `최근 ${days}일`
  const maxF = Math.max(1, d.analyst, d.dream)
  return (
    <>
      <div className="adstats">
        <div className="card adstat"><p className="adstat__label">{period} AI 호출</p><p className="adstat__value">{nf.format(summary.callCount)}회</p><p className="adstat__sub">서비스 하루 한도 {nf.format(limit.dailyCallLimit)}회</p></div>
        <div className="card adstat"><p className="adstat__label">실패</p><p className="adstat__value">{nf.format(summary.failedCount)}건</p><p className="adstat__sub">막은 요청 {nf.format(summary.blockedCount)}건은 호출에 안 세요</p></div>
        <div className="card adstat"><p className="adstat__label">평균 응답 시간</p><p className="adstat__value">{avg ? `${avg}초` : '-'}</p><p className="adstat__sub">최근 기록 {times.length}건 기준</p></div>
        <div className="card adstat"><p className="adstat__label">비용 추정 (USD)</p><p className="adstat__value">{usd(summary.totalCostUsd)}</p><p className="adstat__sub">이번 달 {usd(limit.monthCostUsd)} / {usd(limit.monthlyCostLimitUsd)}</p></div>
      </div>

      <div className="adrow adrow--even">
        <section className="card adpanel">
          <div className="adpanel__head"><h2>한도 현황</h2><span className="adpill">관리자는 한도 면제</span></div>
          <Meter label="서비스 하루 호출" text={`${nf.format(limit.todayCalls)} / ${nf.format(limit.dailyCallLimit)}회 · ${Math.round(limit.dailyUsagePercent)}%`} pct={limit.dailyUsagePercent} />
          <Meter label="이번 달 비용" text={`${usd(limit.monthCostUsd)} / ${usd(limit.monthlyCostLimitUsd)} · ${Math.round(limit.monthUsagePercent)}%`} pct={limit.monthUsagePercent} />
          <p className="adnote">서비스 하루 호출·월 비용 한도를 넘으면 AI 기능만 멈추고 다른 기능은 계속 이용돼요. 막힌 요청은 호출·비용에 더해지지 않아요.</p>
          <table className="adtable">
            <thead><tr><th>항목</th><th>값</th><th>설명</th></tr></thead>
            <tbody>
              <tr><td>월 비용 한도</td><td>{usd(limit.monthlyCostLimitUsd)}</td><td>초과 시 AI 중지</td></tr>
              <tr><td>서비스 하루 호출</td><td>{nf.format(limit.dailyCallLimit)}회</td><td>초과 시 AI 중지</td></tr>
            </tbody>
          </table>
          <p className="adnote adnote--s">값은 서버 설정(application.yaml)에서 바꾸고, 서버를 다시 켜면 반영돼요.</p>
        </section>

        <section className="card adpanel">
          <div className="adpanel__head"><h2>기능별 호출</h2><span>{period}</span></div>
          {[['ANALYST', d.analyst], ['DREAM', d.dream]].map(([k, n]) => (
            <div key={k} className="adbarrow"><span>{FEATURE[k]}</span><div className="adstat__bar"><i style={{ width: `${(n / maxF) * 100}%` }} /></div><b>{nf.format(n)}</b></div>
          ))}
          <div className="adpanel__head adpanel__head--sp"><h2>고정 응답 정책 코드별 건수</h2><span>AI 호출·횟수 차감 없음</span></div>
          {summary.policyCounts.length === 0 ? (
            <p className="adempty">막힌 요청이 없어요.</p>
          ) : (
            <table className="adtable">
              <thead><tr><th>정책 코드</th><th>내용</th><th className="adtable__r">건수</th></tr></thead>
              <tbody>
                {summary.policyCounts.map((p) => (
                  <tr key={p.policyCode}><td className="admono">{p.policyCode}</td><td>{POLICY[p.policyCode] || '-'}</td><td className="adtable__r">{nf.format(p.count)}</td></tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>

      <section className="card adpanel adpanel--wide">
        <div className="adpanel__head"><h2>실패 로그</h2><span>{period} · 총 {nf.format(d.failedTotal)}건 중 최근 10건</span></div>
        {d.failed.length === 0 ? (
          <p className="adempty">실패한 호출이 없어요.</p>
        ) : (
          <table className="adtable">
            <thead><tr><th>시각</th><th>기능</th><th>모델</th><th>사유</th></tr></thead>
            <tbody>
              {d.failed.map((f) => (
                <tr key={f.id}><td>{dt(f.createdAt)}</td><td>{FEATURE[f.featureType] || f.featureType}</td><td>{f.modelName || '-'}</td><td>{f.errorMessage || '-'}</td></tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}
