import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import Pagination from '../../components/ui/Pagination.jsx'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { fetchRoundChoices } from '../../features/admin/issuesAdminApi.js'
import { VERIFY_PAGE_SIZE, fetchVerifyCounts, fetchVerifyList, runVerify } from '../../features/admin/verifyAdminApi.js'

const nf = new Intl.NumberFormat('ko-KR')
const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const TABS = [['result', '검증 결과'], ['pending', '미검증'], ['error', '실패 로그']]

export default function AdminVerifyPage() {
  const toast = useToast()
  const [round, setRound] = useState('')
  const [tab, setTab] = useState('result')
  const [page, setPage] = useState(0)
  const [busy, setBusy] = useState(false)
  const choices = useApi(fetchRoundChoices, [])
  const counts = useApi((signal) => fetchVerifyCounts(signal, round), [round])
  const list = useApi((signal) => fetchVerifyList(signal, { tab, roundNo: round, page }), [tab, round, page])

  const items = list.data?.items || []
  const totalPages = Math.max(1, Math.ceil((list.data?.totalCount || 0) / VERIFY_PAGE_SIZE))
  const c = counts.data
  const done = c ? Math.max(0, c.total - c.pending - c.error) : 0

  const reloadAll = () => { counts.reload(); list.reload() }
  const onRun = async () => {
    if (busy) return
    setBusy(true)
    try {
      const r = await runVerify()
      toast(r?.message || '검증을 실행했어요.')
      reloadAll()
    } catch (e) {
      toast(e?.message || '검증을 실행하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="adverify">
      <header className="adhead">
        <div>
          <h1>당첨 결과 검증</h1>
          <p>등수는 서버 계산으로만 결정돼요.</p>
        </div>
        <div className="adtools">
          <select className="adtools__in" value={round} onChange={(e) => { setRound(e.target.value); setPage(0) }} aria-label="회차">
            <option value="">회차 전체</option>
            {(choices.data || []).map((n) => <option key={n} value={n}>{n}회</option>)}
          </select>
          <button type="button" className="adfilter__btn" onClick={onRun} disabled={busy}>{busy ? '검증 중…' : '검증 수동 실행'}</button>
        </div>
      </header>

      <div className="adstats">
        <div className="card adstat"><p className="adstat__label">전체 발급</p><p className="adstat__value">{c ? `${nf.format(c.total)}건` : '-'}</p><p className="adstat__sub">{round ? `${round}회` : '전체 회차'}</p></div>
        <div className="card adstat"><p className="adstat__label">검증 완료</p><p className="adstat__value">{c ? `${nf.format(done)}건` : '-'}</p><p className="adstat__sub">{c && c.total ? `${Math.round((done / c.total) * 100)}%` : ' '}</p></div>
        <div className="card adstat"><p className="adstat__label">미검증</p><p className="adstat__value">{c ? `${nf.format(c.pending)}건` : '-'}</p></div>
        <div className="card adstat"><p className="adstat__label">검증 실패</p><p className="adstat__value">{c ? `${nf.format(c.error)}건` : '-'}</p></div>
      </div>

      <section className="card adpanel adpanel--wide">
        <div className="adtabs">
          {TABS.map(([k, label]) => (
            <button key={k} type="button" className={`adtabs__b${tab === k ? ' is-on' : ''}`} onClick={() => { setTab(k); setPage(0) }}>{label}</button>
          ))}
        </div>
        {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
        {!list.data && list.loading && <Loading />}
        {list.data && items.length === 0 && <EmptyState title="표시할 내용이 없어요">{tab === 'result' ? '당첨(1~5등)으로 검증된 발급 건만 이곳에 나와요.' : '해당하는 발급 건이 없어요.'}</EmptyState>}
        {list.data && items.length > 0 && tab === 'result' && (
          <table className="adtable">
            <thead><tr><th>발급코드</th><th>회차</th><th>검증 시각</th><th>일치 개수</th><th>등수</th><th>공개</th></tr></thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.recordId}>
                  <td><b>{r.issueCode}</b></td>
                  <td>{r.roundNo}</td>
                  <td>{dt(r.verifiedAt)}</td>
                  <td>{r.matchCount}개{r.bonusMatched ? ' (보너스 포함)' : ''}</td>
                  <td><span className="adpill">{r.winningRank}등</span></td>
                  <td>{r.publicVisible ? '공개' : '비공개'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {list.data && items.length > 0 && tab !== 'result' && (
          <table className="adtable">
            <thead><tr><th>발급코드</th><th>회차</th><th>발급일시</th><th>검증 게임</th><th>상태</th><th>사유</th></tr></thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.issueId}>
                  <td><b>{r.issueCode}</b></td>
                  <td>{r.targetRoundNo}</td>
                  <td>{dt(r.createdAt)}</td>
                  <td>{r.verifiedGames}/{r.gameCount}{r.errorGames ? ` (실패 ${r.errorGames})` : ''}</td>
                  <td><span className={`adpill${r.status === 'ERROR' ? ' is-bad' : ''}`}>{r.statusLabel}</span></td>
                  <td>{r.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={list.loading} />
      </section>
    </div>
  )
}
