import useApi from '../../hooks/useApi.js'
import LottoBall from '../../components/ui/LottoBall.jsx'
import { ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { fetchDashboardAll } from '../../features/admin/dashboardApi.js'

const nf = new Intl.NumberFormat('ko-KR')
const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  if (!s) return '-'
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const hm = (s) => {
  const d = new Date(s)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const JOBS = {
  DRAW_COLLECT: '회차 데이터 수집',
  DRAW_COLLECT_SCHEDULED: '회차 데이터 수집',
  DRAW_COLLECT_STARTUP: '회차 데이터 수집(시작 시)',
  DRAW_COLLECT_MANUAL: '회차 데이터 수집(수동)',
  VERIFY_BATCH: '당첨 결과 검증',
  STAT_SNAPSHOT: '통계 사전 계산',
  STAT_SNAPSHOT_MANUAL: '통계 사전 계산(수동)',
  STAT_ANALYSIS: '통계 분석',
  STAT_GENERATE: '통계 생성',
  STAT_PATTERN: '패턴 통계',
}
const STATUS = { SUCCESS: '성공', FAILED: '실패', RUNNING: '실행 중' }
const TYPES = { TIMEOUT: '응답 시간 초과' }

function Stat({ label, value, sub, bar }) {
  return (
    <div className="card adstat">
      <p className="adstat__label">{label}</p>
      <p className="adstat__value">{value}</p>
      {bar !== undefined && (
        <div className="adstat__bar"><i style={{ width: `${Math.min(100, bar)}%` }} /></div>
      )}
      {sub && <p className="adstat__sub">{sub}</p>}
    </div>
  )
}

function Trend({ daily }) {
  const max = Math.max(1, ...daily.map((d) => d.issues))
  return (
    <div className="adtrend" role="img" aria-label="최근 14일 번호 발급 건수">
      {daily.map((d) => (
        <div key={d.date} className="adtrend__col" title={`${d.date} · ${nf.format(d.issues)}건`}>
          <span className="adtrend__n">{d.issues ? nf.format(d.issues) : ''}</span>
          <i style={{ height: `${Math.max(2, (d.issues / max) * 100)}%` }} />
          <span className="adtrend__d">{d.date.slice(8)}</span>
        </div>
      ))}
    </div>
  )
}

export default function AdminDashboardPage() {
  const { data, error, loading, reload } = useApi(fetchDashboardAll, [])

  return (
    <div className="addash">
      <header className="adhead">
        <div>
          <h1>대시보드</h1>
          <p>실시간 기준 · {data ? dt(data.loadedAt) : '불러오는 중'}</p>
        </div>
        <button type="button" className="adhead__btn" onClick={reload} disabled={loading}>새로고침</button>
      </header>

      {error && <ErrorState error={error} onRetry={reload} />}
      {!data && loading && <Loading />}
      {data && <Body {...data} />}
    </div>
  )
}

function Body({ dash, usage, errors, draw }) {
  const { issues, ai, aiFailedToday, errors: err, latestRound, scheduler } = dash
  const errSub = err.byType?.length ? err.byType.map((t) => `${TYPES[t.errorType] || t.errorType} ${t.count}`).join(' · ') : '최근 24시간 오류 없음'
  return (
    <>
      <div className="adstats">
        <Stat label="오늘 번호 발급" value={`${nf.format(issues.today)}건`} sub={`이번 달 ${nf.format(issues.thisMonth)}건 · 전체 ${nf.format(issues.total)}건`} />
        <Stat label="오늘 AI 이용" value={`${nf.format(ai.todayCalls)}건`} sub={`실패 ${nf.format(aiFailedToday)}건`} />
        <Stat label="AI 일일 한도 사용률" value={`${ai.dailyUsagePercent}%`} bar={ai.dailyUsagePercent} sub={`하루 ${nf.format(ai.dailyCallLimit)}건 중 ${nf.format(ai.todayCalls)}건`} />
        <Stat label="최근 24시간 오류" value={`${nf.format(err.last24Hours)}건`} sub={errSub} />
      </div>

      <div className="adrow">
        <section className="card adpanel">
          <div className="adpanel__head"><h2>일별 번호 발급</h2><span>최근 14일 · 건</span></div>
          <Trend daily={usage.daily} />
        </section>

        <section className="card adpanel">
          <div className="adpanel__head">
            <h2>최신 회차 상태</h2>
            {latestRound.nextStatLabel && <span className="adpill">{latestRound.nextRoundNo}회 통계 {latestRound.nextStatLabel}</span>}
          </div>
          {latestRound.latestRoundNo ? (
            <>
              <p className="adround">{latestRound.latestRoundNo}회</p>
              <dl className="adkv">
                <div><dt>추첨일</dt><dd>{latestRound.drawDate}</dd></div>
                <div><dt>다음 회차</dt><dd>{latestRound.nextRoundNo}회</dd></div>
              </dl>
              {draw && (
                <div className="adballs">
                  {draw.numbers.map((n) => <LottoBall key={n} n={n} size={30} />)}
                  <b>+</b>
                  <LottoBall n={draw.bonusNumber} size={30} />
                </div>
              )}
            </>
          ) : (
            <p className="adempty">수집된 회차가 아직 없어요.</p>
          )}
        </section>
      </div>

      <div className="adrow adrow--even">
        <section className="card adpanel">
          <div className="adpanel__head"><h2>스케줄러 상태</h2></div>
          {scheduler.length === 0 ? (
            <p className="adempty">실행 기록이 아직 없어요.</p>
          ) : (
            <table className="adtable">
              <thead><tr><th>작업</th><th>마지막 실행</th><th>결과</th></tr></thead>
              <tbody>
                {scheduler.map((s) => (
                  <tr key={s.jobName}>
                    <td>{JOBS[s.jobName] || s.jobName}</td>
                    <td>{dt(s.startedAt)}</td>
                    <td><span className={`adpill${s.jobStatus === 'FAILED' || s.interrupted ? ' is-bad' : ''}`}>{s.interrupted ? '중단됨' : STATUS[s.jobStatus] || s.jobStatus}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="card adpanel">
          <div className="adpanel__head"><h2>최근 오류</h2></div>
          {errors.length === 0 ? (
            <p className="adempty">기록된 오류가 없어요.</p>
          ) : (
            <table className="adtable">
              <thead><tr><th>시각</th><th>구분</th><th>내용</th></tr></thead>
              <tbody>
                {errors.map((e) => (
                  <tr key={e.id}>
                    <td>{dt(e.createdAt)}</td>
                    <td>{e.apiName}</td>
                    <td>{TYPES[e.errorType] || e.errorMessage || e.errorType}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </>
  )
}
