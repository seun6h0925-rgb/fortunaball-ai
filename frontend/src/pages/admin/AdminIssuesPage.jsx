import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import LottoBall from '../../components/ui/LottoBall.jsx'
import Pagination from '../../components/ui/Pagination.jsx'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { ISSUE_PAGE_SIZE, fetchIssues, fetchRoundChoices } from '../../features/admin/issuesAdminApi.js'

const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const LETTERS = 'ABCDEFGHIJ'
const EMPTY = { issueCode: '', roundNo: '', guestToken: '' }

export default function AdminIssuesPage() {
  const [draft, setDraft] = useState(EMPTY)
  const [applied, setApplied] = useState(EMPTY)
  const [page, setPage] = useState(0)
  const [pickId, setPickId] = useState(null)
  const choices = useApi(fetchRoundChoices, [])
  const list = useApi((signal) => fetchIssues(signal, { ...applied, page }), [applied, page])

  const items = list.data?.items || []
  const picked = items.find((i) => i.issueId === pickId) || items[0]
  const totalPages = Math.max(1, Math.ceil((list.data?.totalCount || 0) / ISSUE_PAGE_SIZE))
  const set = (k) => (e) => setDraft((d) => ({ ...d, [k]: e.target.value }))
  const submit = (e) => {
    e.preventDefault()
    setPage(0)
    setPickId(null)
    setApplied({ issueCode: draft.issueCode.trim(), roundNo: draft.roundNo, guestToken: draft.guestToken.trim() })
  }

  return (
    <div className="adissues">
      <header className="adhead">
        <div>
          <h1>번호 발급 기록</h1>
          <p>발급코드와 게스트 식별값은 일부만 표시돼요.</p>
        </div>
      </header>

      <form className="card adfilter" onSubmit={submit}>
        <input className="adtools__in adfilter__code" placeholder="발급코드 검색" value={draft.issueCode} onChange={set('issueCode')} />
        <select className="adtools__in" value={draft.roundNo} onChange={set('roundNo')} aria-label="회차">
          <option value="">회차 전체</option>
          {(choices.data || []).map((n) => <option key={n} value={n}>{n}회</option>)}
        </select>
        <input className="adtools__in adfilter__guest" placeholder="게스트 식별값 (전체 값)" value={draft.guestToken} onChange={set('guestToken')} />
        <button type="submit" className="adfilter__btn">조회</button>
      </form>

      {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
      {!list.data && list.loading && <Loading />}
      {list.data && (
        <div className="adsplit">
          <section className="card adpanel">
            {items.length === 0 ? (
              <EmptyState title="발급 기록이 없어요">조건을 바꿔서 다시 조회해 보세요.</EmptyState>
            ) : (
              <table className="adtable adtable--click">
                <thead><tr><th>발급코드</th><th>회차</th><th>발급일시</th><th>게임</th><th>상태</th><th>게스트 식별값</th></tr></thead>
                <tbody>
                  {items.map((i) => (
                    <tr key={i.issueId} className={i.issueId === picked?.issueId ? 'is-sel' : ''} onClick={() => setPickId(i.issueId)}>
                      <td><b>{i.issueCode}</b></td>
                      <td>{i.targetRoundNo}</td>
                      <td>{dt(i.createdAt)}</td>
                      <td>{i.gameCount}</td>
                      <td><span className={`adpill${i.verificationStatus === 'ERROR' ? ' is-bad' : ''}`}>{i.verificationLabel}</span></td>
                      <td>{i.guestToken}…</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={list.loading} />
          </section>

          <aside className="card adpanel adpanel--detail">
            <div className="adpanel__head"><h2>발급 상세</h2>{picked && <span className="adpill">{picked.verificationLabel}</span>}</div>
            {picked ? (
              <>
                <p className="addetail__code">코드 <b>{picked.issueCode}</b></p>
                <p className="addetail__meta">{picked.targetRoundNo}회 · {dt(picked.createdAt)} 발급 · {picked.gameCount}게임</p>
                <div className="addetail__games">
                  {picked.games.map((g, idx) => (
                    <div key={idx} className="addetail__game">
                      <span>{LETTERS[idx] || idx + 1}</span>
                      {g.map((n) => <LottoBall key={n} n={n} size={30} />)}
                    </div>
                  ))}
                </div>
                <p className="adnote adnote--s">발급번호는 오름차순으로 저장돼요. 일치 개수와 등수는 서버 검증 결과예요.</p>
              </>
            ) : (
              <p className="adempty">왼쪽에서 발급 기록을 선택해 주세요.</p>
            )}
          </aside>
        </div>
      )}
    </div>
  )
}
