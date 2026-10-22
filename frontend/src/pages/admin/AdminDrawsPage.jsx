import { useMemo, useState } from 'react'
import useApi from '../../hooks/useApi.js'
import LottoBall from '../../components/ui/LottoBall.jsx'
import Pagination from '../../components/ui/Pagination.jsx'
import Icon from '../../components/ui/Icon.jsx'
import { ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { fetchMissingAll, fetchNumbers, fetchRoundList, recalcRound, resyncRound } from '../../features/admin/drawsAdminApi.js'

const SIZES = [6, 12, 30]
const RANKS = [1, 2, 3, 4, 5]
const pad = (n) => String(n).padStart(2, '0')
const md = (s) => (s ? `${s.slice(5, 7)}.${s.slice(8, 10)}` : '-')
const full = (s) => (s ? s.replaceAll('-', '.') : '-')
const clock = (s) => {
  if (!s) return '-'
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const nextDate = (s) => {
  if (!s) return '-'
  const d = new Date(`${s}T00:00:00`)
  d.setDate(d.getDate() + 7)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} (토) 20:35`
}

export default function AdminDrawsPage() {
  const toast = useToast()
  const list = useApi(fetchRoundList, [])
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(6)
  const [q, setQ] = useState('')
  const [busy, setBusy] = useState('')
  const [missing, setMissing] = useState(null) // null: 아직 안 확인
  const [recalced, setRecalced] = useState({})

  const latest = list.data?.latest || 0
  const all = useMemo(() => (list.data?.items || []).filter((i) => i.roundNo <= latest), [list.data, latest])
  const rows = useMemo(() => {
    const t = q.trim()
    return all.filter((i) => !t || String(i.roundNo).includes(t))
  }, [all, q])
  const totalPages = Math.max(1, Math.ceil(rows.length / size))
  const cur = Math.min(page, totalPages - 1)
  const shown = rows.slice(cur * size, cur * size + size)
  const key = shown.map((r) => r.roundNo).join(',')
  const nums = useApi((signal) => fetchNumbers(signal, shown.filter((r) => !r.missing).map((r) => r.roundNo)), [key])

  const statRows = useMemo(() => (list.data?.items || []).filter((i) => i.statStatus).slice(0, 4), [list.data])
  const latestItem = all[0]
  const missingInList = all.filter((i) => i.missing).length

  const run = async (id, fn, done) => {
    if (busy) return
    setBusy(id)
    try {
      const r = await fn()
      done(r)
    } catch (e) {
      toast(e?.message || '작업하지 못했어요.')
    } finally {
      setBusy('')
    }
  }
  const onResync = (no) =>
    run(`r${no}`, () => resyncRound(no), (r) => {
      toast(`${no}회 재수집을 마쳤어요. ${r?.prizeCheckMessage || ''}`.trim())
      list.reload()
    })
  const onRecalc = (no) =>
    run(`s${no}`, () => recalcRound(no), (r) => {
      setRecalced((m) => ({ ...m, [no]: r }))
      toast(`${no}회 통계를 다시 계산했어요.`)
      list.reload()
    })
  const onMissing = () =>
    run('missing', () => fetchMissingAll(latest), (m) => {
      setMissing(m)
      toast(m.length ? `누락 회차가 ${m.length}개 있어요.` : '누락 회차가 없어요.')
    })

  return (
    <div className="addraws">
      <header className="adhead">
        <div>
          <h1>회차 데이터 관리</h1>
          <p>공식 API로만 수집해요. 수동 입력·수정은 지원하지 않아요.</p>
        </div>
        <button type="button" className="adhead__btn" onClick={onMissing} disabled={!latest || busy === 'missing'}>
          {busy === 'missing' ? '확인하는 중…' : '누락 회차 확인'}
        </button>
      </header>

      {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
      {!list.data && list.loading && <Loading />}
      {list.data && (
        <>
          <div className="adstats">
            <div className="card adstat"><p className="adstat__label">최신 회차</p><p className="adstat__value">{latest}회</p><p className="adstat__sub">{full(latestItem?.drawDate)} 수집 완료</p></div>
            <div className="card adstat"><p className="adstat__label">누락 회차</p><p className="adstat__value">{missing ? missing.length : missingInList}개</p><p className="adstat__sub">{missing ? `전체 1~${latest}회 확인` : `최근 ${all.length}회 기준 · 전체는 위 버튼`}</p></div>
            <div className="card adstat"><p className="adstat__label">1~5등 검증</p><p className="adstat__value">{latestItem?.prizeRanksCollected ?? 0}/5</p><p className="adstat__sub">최신 회차 기준</p></div>
            <div className="card adstat"><p className="adstat__label">다음 추첨</p><p className="adstat__value">{latest + 1}회</p><p className="adstat__sub">{nextDate(latestItem?.drawDate)}</p></div>
          </div>

          <section className="card adpanel adpanel--wide">
            <div className="adpanel__head">
              <h2>회차 목록</h2>
              <div className="adtools">
                <input className="adtools__in" placeholder="회차 검색" inputMode="numeric" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} />
                <select className="adtools__in" value={size} onChange={(e) => { setSize(Number(e.target.value)); setPage(0) }} aria-label="한 쪽에 보는 개수">
                  {SIZES.map((s) => <option key={s} value={s}>{s}개씩 보기</option>)}
                </select>
              </div>
            </div>
            {shown.length === 0 ? (
              <p className="adempty">조건에 맞는 회차가 없어요.</p>
            ) : (
              <div className="adlist">
                {shown.map((r) => {
                  const n = nums.data?.[r.roundNo]
                  const state = r.missing ? '누락' : r.prizeStatus === 'COMPLETE' ? '성공' : '일부 수집'
                  return (
                    <div key={r.roundNo} className="adlist__row">
                      <b className="adlist__no">{r.roundNo}회</b>
                      <span className="adlist__date">{md(r.drawDate)}</span>
                      <div className="adlist__balls">
                        {n ? (<>{n.numbers.map((x) => <LottoBall key={x} n={x} size={28} />)}<span>+</span><LottoBall n={n.bonus} size={28} /></>) : <span className="adlist__dim">{r.missing ? '데이터 없음' : '불러오는 중'}</span>}
                      </div>
                      <div className="adlist__ranks">
                        {RANKS.map((k) => (
                          <span key={k} className={`adchk${k <= r.prizeRanksCollected ? '' : ' is-off'}`}>{k}등 {k <= r.prizeRanksCollected && <Icon name="check" size={11} strokeWidth={3} />}</span>
                        ))}
                      </div>
                      <span className={`adpill${state === '성공' ? '' : ' is-bad'}`}>{state}</span>
                      <button type="button" className="adbtn" onClick={() => onResync(r.roundNo)} disabled={!!busy}>{busy === `r${r.roundNo}` ? '수집 중…' : '재수집'}</button>
                    </div>
                  )
                })}
              </div>
            )}
            <Pagination page={cur} totalPages={totalPages} onChange={setPage} />
          </section>

          <section className="card adpanel adpanel--wide">
            <div className="adpanel__head"><h2>회차별 통계 상태</h2><span>토요일 20:00 대상 회차 전환 · 20:40부터 결과 확인(자동)</span></div>
            <p className="adnote">추첨 결과가 올라오면 통계가 자동으로 확정되고 다음 회차 준비 통계가 만들어져요. 실패하면 이전 통계를 그대로 두고 알림을 보내요. 아래 버튼은 자동 작업이 실패했을 때만 쓰면 돼요.</p>
            {statRows.length === 0 ? (
              <p className="adempty">통계 기록이 아직 없어요.</p>
            ) : (
              <table className="adtable">
                <thead><tr><th>회차</th><th>상태</th><th>계산 기준</th><th>생성 시각</th><th /></tr></thead>
                <tbody>
                  {statRows.map((s) => {
                    const r = recalced[s.roundNo]
                    return (
                      <tr key={s.roundNo}>
                        <td><b>{s.roundNo}회</b></td>
                        <td><span className="adpill">{r?.statStatus === 'CONFIRMED' ? '확정' : s.statLabel}</span></td>
                        <td>{r ? `${r.basisRoundNo}회까지의 결과 기준` : '-'}</td>
                        <td>{r ? clock(r.generatedAt) : '-'}</td>
                        <td className="adtable__r"><button type="button" className="adbtn" onClick={() => onRecalc(s.roundNo)} disabled={!!busy}>{busy === `s${s.roundNo}` ? '계산 중…' : s.statStatus === 'PREPARING' ? '다시 만들기' : '다시 계산'}</button></td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
            <p className="adnote adnote--s">사전 분석 통계는 확정 후에도 지우지 않고 보관해요. 상태는 사전 분석 → 결과 대기 → 확정 순서로 바뀌어요.</p>
          </section>

          <section className="card adpanel adpanel--wide adpanel--soft">
            <h2 className="adpanel__sub">누락 회차 확인 결과</h2>
            {missing === null ? (
              <p className="adnote">위의 "누락 회차 확인" 버튼을 누르면 1회부터 최신 회차까지 빠진 회차를 찾아요.</p>
            ) : missing.length === 0 ? (
              <p className="adnote">누락 없음. 누락이 생기면 여기에 회차 번호가 표시되고 재수집 버튼으로 다시 가져올 수 있어요.</p>
            ) : (
              <div className="admiss">
                {missing.map((m) => <button key={m} type="button" className="adbtn" onClick={() => onResync(m)} disabled={!!busy}>{m}회 재수집</button>)}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
