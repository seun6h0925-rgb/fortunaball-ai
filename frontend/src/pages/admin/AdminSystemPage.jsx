import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import Pagination from '../../components/ui/Pagination.jsx'
import { ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { LOG_PAGE_SIZE, RUNNABLE_JOBS, fetchErrorLogs, fetchHealth, fetchSchedulerLogs, runJob } from '../../features/admin/systemAdminApi.js'

const nf = new Intl.NumberFormat('ko-KR')
const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  if (!s) return '-'
  const d = new Date(s)
  return `${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const JOBS = {
  DRAW_COLLECT: '회차 데이터 수집', DRAW_COLLECT_SCHEDULED: '회차 데이터 수집', DRAW_COLLECT_STARTUP: '회차 데이터 수집(시작 시)',
  DRAW_COLLECT_MANUAL: '회차 데이터 수집(수동)', VERIFY_BATCH: '당첨 결과 검증', STAT_SNAPSHOT: '통계 사전 계산', STAT_SNAPSHOT_MANUAL: '통계 사전 계산(수동)',
}
const STATUS = { SUCCESS: '성공', FAILED: '실패', RUNNING: '실행 중' }
const FRESH = [['회차 당첨번호', 'DRAW_COLLECT'], ['당첨 결과 검증', 'VERIFY_BATCH'], ['통계 사전 계산', 'STAT_SNAPSHOT']]
const baseJob = (n) => (n || '').replace(/_(SCHEDULED|STARTUP|MANUAL)$/, '')

export default function AdminSystemPage() {
  const toast = useToast()
  const health = useApi(fetchHealth, [])
  const [job, setJob] = useState('')
  const [jobPage, setJobPage] = useState(0)
  const [errType, setErrType] = useState('')
  const [errPage, setErrPage] = useState(0)
  const [busy, setBusy] = useState('')
  const logs = useApi((signal) => fetchSchedulerLogs(signal, { jobName: job, page: jobPage }), [job, jobPage])
  const errs = useApi((signal) => fetchErrorLogs(signal, { errorType: errType, page: errPage }), [errType, errPage])

  const reloadAll = () => { health.reload(); logs.reload(); errs.reload() }
  const run = async (name) => {
    if (busy) return
    setBusy(name)
    try {
      const r = await runJob(name)
      toast(r?.message || '작업을 실행했어요.')
      reloadAll()
    } catch (e) {
      toast(e?.message || '작업을 실행하지 못했어요.')
    } finally {
      setBusy('')
    }
  }

  const dash = health.data?.dash
  const types = dash?.errors?.byType || []
  const sched = dash?.scheduler || []
  const lastOf = (name) => sched.find((s) => baseJob(s.jobName) === name)
  const logItems = logs.data?.items || []
  const errItems = errs.data?.items || []
  const pages = (d) => Math.max(1, Math.ceil((d?.totalCount || 0) / LOG_PAGE_SIZE))

  return (
    <div className="adsys">
      <header className="adhead">
        <div><h1>시스템 운영</h1><p>서버와 자동 작업 상태</p></div>
        <button type="button" className="adhead__btn" onClick={reloadAll}>새로고침</button>
      </header>

      {health.error && <ErrorState error={health.error} onRetry={health.reload} />}
      {!health.data && health.loading && <Loading />}
      {health.data && (
        <div className="adstats">
          <div className="card adstat"><p className="adstat__label">API 상태</p><p className="adstat__value">정상</p><p className="adstat__sub">방금 응답함</p></div>
          <div className="card adstat"><p className="adstat__label">응답 시간</p><p className="adstat__value">{health.data.ms}ms</p><p className="adstat__sub">대시보드 조회 기준</p></div>
          <div className="card adstat"><p className="adstat__label">최근 24시간 오류</p><p className="adstat__value">{nf.format(dash.errors.last24Hours)}건</p><p className="adstat__sub">외부 API 오류 기준</p></div>
          <div className="card adstat"><p className="adstat__label">DB 연결</p><p className="adstat__value">정상</p><p className="adstat__sub">데이터를 읽어 왔어요</p></div>
        </div>
      )}

      <div className="adrow adrow--even">
        <section className="card adpanel">
          <div className="adpanel__head">
            <h2>스케줄러 실행 로그</h2>
            <select className="adtools__in" value={job} onChange={(e) => { setJob(e.target.value); setJobPage(0) }} aria-label="작업">
              <option value="">전체 작업</option>
              {RUNNABLE_JOBS.map((j) => <option key={j} value={j}>{JOBS[j]}</option>)}
            </select>
          </div>
          {logs.error && <ErrorState error={logs.error} onRetry={logs.reload} />}
          {!logs.data && logs.loading && <Loading />}
          {logs.data && logItems.length === 0 && <p className="adempty">실행 기록이 없어요.</p>}
          {logItems.length > 0 && (
            <table className="adtable">
              <thead><tr><th>작업</th><th>실행 시각</th><th>결과</th><th>처리</th><th /></tr></thead>
              <tbody>
                {logItems.map((l) => (
                  <tr key={l.id}>
                    <td>{JOBS[l.jobName] || l.jobName}</td>
                    <td>{dt(l.startedAt)}</td>
                    <td><span className={`adpill${l.jobStatus === 'FAILED' || l.interrupted ? ' is-bad' : ''}`}>{l.interrupted ? '중단됨' : STATUS[l.jobStatus] || l.jobStatus}</span></td>
                    <td>{l.processedCount != null ? `${nf.format(l.processedCount)}건` : '-'}</td>
                    <td className="adtable__r">{RUNNABLE_JOBS.includes(baseJob(l.jobName)) && <button type="button" className="adbtn" disabled={!!busy} onClick={() => run(baseJob(l.jobName))}>{busy === baseJob(l.jobName) ? '실행 중…' : '실행'}</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <Pagination page={jobPage} totalPages={pages(logs.data)} onChange={setJobPage} disabled={logs.loading} />
        </section>

        <section className="card adpanel">
          <div className="adpanel__head"><h2>데이터 업데이트 상태</h2></div>
          <table className="adtable">
            <thead><tr><th>데이터</th><th>마지막 갱신</th><th>상태</th><th /></tr></thead>
            <tbody>
              {FRESH.map(([label, name]) => {
                const l = lastOf(name)
                return (
                  <tr key={name}>
                    <td>{label}</td>
                    <td>{l ? dt(l.endedAt || l.startedAt) : '-'}</td>
                    <td>{l ? <span className={`adpill${l.jobStatus === 'SUCCESS' && !l.interrupted ? '' : ' is-bad'}`}>{l.jobStatus === 'SUCCESS' && !l.interrupted ? '최신' : '확인 필요'}</span> : <span className="adpill is-bad">기록 없음</span>}</td>
                    <td className="adtable__r"><button type="button" className="adbtn" disabled={!!busy} onClick={() => run(name)}>{busy === name ? '실행 중…' : '지금 실행'}</button></td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <p className="adnote adnote--s">실패한 수집은 자동 재시도하고, 그래도 실패하면 이 화면에 표시돼요. "지금 실행"은 자동 작업이 실패했을 때만 쓰세요.</p>
        </section>
      </div>

      <section className="card adpanel adpanel--wide">
        <div className="adpanel__head">
          <h2>오류 로그</h2>
          <div className="adtabs adtabs--tight">
            <button type="button" className={`adtabs__b${errType === '' ? ' is-on' : ''}`} onClick={() => { setErrType(''); setErrPage(0) }}>전체</button>
            {types.map((t) => <button key={t.errorType} type="button" className={`adtabs__b${errType === t.errorType ? ' is-on' : ''}`} onClick={() => { setErrType(t.errorType); setErrPage(0) }}>{t.errorType}</button>)}
          </div>
        </div>
        {errs.error && <ErrorState error={errs.error} onRetry={errs.reload} />}
        {!errs.data && errs.loading && <Loading />}
        {errs.data && errItems.length === 0 && <p className="adempty">기록된 오류가 없어요.</p>}
        {errItems.length > 0 && (
          <table className="adtable">
            <thead><tr><th>시각</th><th>구분</th><th>유형</th><th>내용</th></tr></thead>
            <tbody>
              {errItems.map((e) => (
                <tr key={e.id}><td>{dt(e.createdAt)}</td><td>{e.apiName}</td><td>{e.errorType}{e.httpStatus ? ` (${e.httpStatus})` : ''}</td><td>{e.errorMessage || '-'}</td></tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination page={errPage} totalPages={pages(errs.data)} onChange={setErrPage} disabled={errs.loading} />
      </section>
    </div>
  )
}
