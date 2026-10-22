import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import LottoBall from '../../components/ui/LottoBall.jsx'
import Pagination from '../../components/ui/Pagination.jsx'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { fetchRoundChoices } from '../../features/admin/issuesAdminApi.js'
import { RECORD_PAGE_SIZE, fetchRecords, fetchWinningNumbers, setRecordPublic } from '../../features/admin/recordsAdminApi.js'

const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const EMPTY = { roundNo: '', rank: '', publicVisible: '' }

export default function AdminRecordsPage() {
  const toast = useToast()
  const [draft, setDraft] = useState(EMPTY)
  const [applied, setApplied] = useState(EMPTY)
  const [page, setPage] = useState(0)
  const [pickId, setPickId] = useState(null)
  const [over, setOver] = useState({}) // 방금 바꾼 공개 여부 (목록을 다시 받기 전까지 화면에 바로 반영)
  const [busy, setBusy] = useState(false)
  const choices = useApi(fetchRoundChoices, [])
  const list = useApi((signal) => fetchRecords(signal, { ...applied, page }), [applied, page])

  const items = list.data?.items || []
  const picked = items.find((r) => r.recordId === pickId) || items[0]
  const pickedRound = picked?.roundNo
  const win = useApi((signal) => (pickedRound ? fetchWinningNumbers(signal, pickedRound) : Promise.resolve(null)), [pickedRound])
  const totalPages = Math.max(1, Math.ceil((list.data?.totalCount || 0) / RECORD_PAGE_SIZE))
  const isPublic = (r) => (r.recordId in over ? over[r.recordId] : r.publicVisible)
  const set = (k) => (e) => setDraft((d) => ({ ...d, [k]: e.target.value }))

  const submit = (e) => {
    e.preventDefault()
    setPage(0)
    setPickId(null)
    setOver({})
    setApplied(draft)
  }
  const toggle = async (r) => {
    if (busy) return
    const next = !isPublic(r)
    setBusy(true)
    try {
      await setRecordPublic(r.recordId, next)
      setOver((o) => ({ ...o, [r.recordId]: next }))
      toast(next ? '공개로 바꿨어요.' : '비공개로 바꿨어요.')
    } catch (e) {
      toast(e?.message || '공개 여부를 바꾸지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="adrecords">
      <header className="adhead">
        <div>
          <h1>우리 서비스 당첨 기록 관리</h1>
          <p>공개로 설정한 기록만 사용자 화면에 보여요.</p>
        </div>
      </header>

      <form className="card adfilter" onSubmit={submit}>
        <select className="adtools__in" value={draft.roundNo} onChange={set('roundNo')} aria-label="회차">
          <option value="">회차 전체</option>
          {(choices.data || []).map((n) => <option key={n} value={n}>{n}회</option>)}
        </select>
        <select className="adtools__in" value={draft.rank} onChange={set('rank')} aria-label="등수">
          <option value="">등수 전체</option>
          {[1, 2, 3, 4, 5].map((k) => <option key={k} value={k}>{k}등</option>)}
        </select>
        <select className="adtools__in" value={draft.publicVisible} onChange={set('publicVisible')} aria-label="공개 여부">
          <option value="">공개 여부 전체</option>
          <option value="true">공개</option>
          <option value="false">비공개</option>
        </select>
        <button type="submit" className="adfilter__btn">조회</button>
      </form>

      {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
      {!list.data && list.loading && <Loading />}
      {list.data && (
        <div className="adsplit">
          <section className="card adpanel">
            {items.length === 0 ? (
              <EmptyState title="당첨 기록이 없어요">조건을 바꿔서 다시 조회해 보세요.</EmptyState>
            ) : (
              <table className="adtable adtable--click">
                <thead><tr><th>ID</th><th>회차</th><th>등수</th><th>발급코드</th><th>검증 시각</th><th>공개</th></tr></thead>
                <tbody>
                  {items.map((r) => (
                    <tr key={r.recordId} className={r.recordId === picked?.recordId ? 'is-sel' : ''} onClick={() => setPickId(r.recordId)}>
                      <td>{r.recordId}</td>
                      <td>{r.roundNo}</td>
                      <td><span className="adpill">{r.winningRank}등</span></td>
                      <td><b>{r.issueCode}</b></td>
                      <td>{dt(r.verifiedAt)}</td>
                      <td>
                        <button type="button" role="switch" aria-checked={isPublic(r)} aria-label={`${r.recordId}번 기록 공개`} className={`adswitch${isPublic(r) ? ' is-on' : ''}`} onClick={(e) => { e.stopPropagation(); toggle(r) }} disabled={busy}><i /></button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={list.loading} />
          </section>

          <aside className="card adpanel adpanel--detail">
            <div className="adpanel__head"><h2>기록 상세</h2>{picked && <span className="adpill">{isPublic(picked) ? '공개' : '비공개'}</span>}</div>
            {picked ? (
              <>
                <p className="addetail__meta">기록 ID {picked.recordId} · {picked.roundNo}회 · {picked.winningRank}등</p>
                <p className="addetail__meta">발급코드 {picked.issueCode}</p>
                <p className="addetail__meta">검증 {dt(picked.verifiedAt)}</p>
                <div className="addetail__game">
                  {picked.numbers.map((n) => <LottoBall key={n} n={n} size={30} off={!!win.data && !win.data.includes(n)} />)}
                </div>
                <p className="adnote adnote--s">밝은 공은 당첨번호와 일치한 번호예요.</p>
                <button type="button" className="adbtn adbtn--wide" onClick={() => toggle(picked)} disabled={busy}>{isPublic(picked) ? '비공개로 변경' : '공개로 변경'}</button>
              </>
            ) : (
              <p className="adempty">왼쪽에서 기록을 선택해 주세요.</p>
            )}
          </aside>
        </div>
      )}
    </div>
  )
}
