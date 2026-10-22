import { useMemo, useState } from 'react'
import useApi from '../../hooks/useApi.js'
import Pagination from '../../components/ui/Pagination.jsx'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { DREAM_PAGE_SIZE, ORIGINS, createSymbol, fetchSymbolCounts, fetchSymbols, updateSymbol } from '../../features/admin/dreamsAdminApi.js'

const nf = new Intl.NumberFormat('ko-KR')
const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const BLANK = { id: null, keyword: '', category: '', description: '', aliases: '', numbers: [], sourceId: '', active: true, history: [], sources: [] }
const toForm = (s) => ({ id: s.id, keyword: s.keyword, category: s.category || '', description: s.description || '', aliases: (s.aliases || []).join(', '), numbers: s.numbers.map((n) => ({ ...n })), sourceId: '', active: s.active, history: s.history || [], sources: s.sources || [] })

export default function AdminDreamsPage() {
  const toast = useToast()
  const [draft, setDraft] = useState({ keyword: '', active: '' })
  const [applied, setApplied] = useState({ keyword: '', active: '' })
  const [page, setPage] = useState(0)
  const [form, setForm] = useState(null)
  const [newNum, setNewNum] = useState({ number: '', origin: 'SERVICE' })
  const [busy, setBusy] = useState(false)
  const counts = useApi(fetchSymbolCounts, [])
  const list = useApi((signal) => fetchSymbols(signal, { ...applied, page }), [applied, page])

  const items = useMemo(() => list.data?.items || [], [list.data])
  const totalPages = Math.max(1, Math.ceil((list.data?.totalCount || 0) / DREAM_PAGE_SIZE))
  // 새 상징에 연결할 수 있는 출처: 지금 불러온 상징들에 이미 연결된 출처들
  const knownSources = useMemo(() => {
    const m = new Map()
    items.forEach((s) => s.sources.forEach((x) => m.set(x.id, x)))
    return [...m.values()]
  }, [items])
  const setF = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))
  const refresh = () => { list.reload(); counts.reload() }

  const submit = (e) => {
    e.preventDefault()
    setPage(0)
    setApplied({ keyword: draft.keyword.trim(), active: draft.active })
  }
  const addNum = () => {
    const n = Number(newNum.number)
    if (!Number.isInteger(n) || n < 1 || n > 45) {
      toast('번호는 1~45 사이 숫자만 넣을 수 있어요.')
      return
    }
    if (form.numbers.some((x) => x.number === n && x.origin === newNum.origin)) return
    setForm((f) => ({ ...f, numbers: [...f.numbers, { number: n, origin: newNum.origin }] }))
    setNewNum({ ...newNum, number: '' })
  }
  const dropNum = (i) => setForm((f) => ({ ...f, numbers: f.numbers.filter((_, k) => k !== i) }))
  const aliasList = () => form.aliases.split(',').map((a) => a.trim()).filter(Boolean)

  const save = async () => {
    if (!form || busy) return
    if (form.numbers.length === 0) { toast('추천 번호를 1개 이상 넣어 주세요.'); return }
    setBusy(true)
    try {
      if (form.id) {
        await updateSymbol(form.id, { description: form.description, aliases: aliasList(), numbers: form.numbers, active: form.active })
        toast('꿈 상징을 수정했어요.')
      } else {
        if (!form.keyword.trim()) { toast('상징 키워드를 적어 주세요.'); setBusy(false); return }
        if (!form.sourceId) { toast('연결할 출처를 골라 주세요.'); setBusy(false); return }
        await createSymbol({ keyword: form.keyword.trim(), category: form.category.trim() || null, description: form.description, aliases: aliasList(), numbers: form.numbers, sources: [{ sourceId: Number(form.sourceId) }] })
        toast('꿈 상징을 등록했어요.')
        setForm(null)
      }
      refresh()
    } catch (e) {
      toast(e?.message || '저장하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }
  const toggle = async (s) => {
    if (busy) return
    setBusy(true)
    try {
      await updateSymbol(s.id, { active: !s.active })
      toast(s.active ? '사용을 껐어요.' : '사용을 켰어요.')
      refresh()
    } catch (e) {
      toast(e?.message || '바꾸지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const c = counts.data
  return (
    <div className="addreams">
      <header className="adhead">
        <div>
          <h1>꿈 상징 데이터 관리</h1>
          <p>AI는 번호를 만들지 않아요. 번호는 이 데이터에서만 가져와요. 번호는 1~45만 저장해요.</p>
        </div>
        <button type="button" className="adfilter__btn" onClick={() => setForm({ ...BLANK })}>상징 등록</button>
      </header>

      <div className="adstats adstats--3">
        <div className="card adstat"><p className="adstat__label">전체 상징</p><p className="adstat__value">{c ? `${nf.format(c.all)}개` : '-'}</p></div>
        <div className="card adstat"><p className="adstat__label">사용 중</p><p className="adstat__value">{c ? `${nf.format(c.on)}개` : '-'}</p></div>
        <div className="card adstat"><p className="adstat__label">사용 안 함</p><p className="adstat__value">{c ? `${nf.format(c.off)}개` : '-'}</p></div>
      </div>

      <form className="card adfilter" onSubmit={submit}>
        <input className="adtools__in adfilter__code" placeholder="상징 키워드 검색" value={draft.keyword} onChange={(e) => setDraft((d) => ({ ...d, keyword: e.target.value }))} />
        <select className="adtools__in" value={draft.active} onChange={(e) => setDraft((d) => ({ ...d, active: e.target.value }))} aria-label="사용 여부">
          <option value="">사용 여부 전체</option>
          <option value="true">사용 중</option>
          <option value="false">사용 안 함</option>
        </select>
        <button type="submit" className="adfilter__btn">조회</button>
      </form>

      <div className="adsplit">
        <section className="card adpanel">
          {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
          {!list.data && list.loading && <Loading />}
          {list.data && items.length === 0 && <EmptyState title="꿈 상징이 없어요">조건을 바꾸거나 상징을 등록해 주세요.</EmptyState>}
          {items.length > 0 && (
            <table className="adtable adtable--click">
              <thead><tr><th>상징 키워드</th><th>분류</th><th>번호 (출처)</th><th>사용</th></tr></thead>
              <tbody>
                {items.map((s) => (
                  <tr key={s.id} className={s.id === form?.id ? 'is-sel' : ''} onClick={() => setForm(toForm(s))}>
                    <td><b>{s.keyword}</b>{s.aliases?.length ? <em className="adalias"> / {s.aliases.join(' / ')}</em> : null}</td>
                    <td>{s.category}</td>
                    <td>{s.numbers.map((n) => <span key={`${n.number}${n.origin}`} className="adnum">{n.number}<i>{ORIGINS[n.origin] || n.origin}</i></span>)}</td>
                    <td><button type="button" role="switch" aria-checked={s.active} aria-label={`${s.keyword} 사용`} className={`adswitch${s.active ? ' is-on' : ''}`} onClick={(e) => { e.stopPropagation(); toggle(s) }} disabled={busy}><i /></button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={list.loading} />
        </section>

        <aside className="card adpanel">
          <div className="adpanel__head"><h2>{!form ? '상징 수정' : form.id ? `상징 수정 · ${form.keyword}` : '상징 등록'}</h2></div>
          {!form ? (
            <p className="adempty">왼쪽에서 상징을 선택하거나 "상징 등록"을 눌러 주세요.</p>
          ) : (
            <>
              <label className="adlbl" htmlFor="ds-key">키워드</label>
              <input id="ds-key" className="adtools__in adform__in" value={form.keyword} onChange={setF('keyword')} disabled={!!form.id} />
              {!form.id && (<><label className="adlbl" htmlFor="ds-cat">분류 (비우면 "기타")</label><input id="ds-cat" className="adtools__in adform__in" value={form.category} onChange={setF('category')} /></>)}
              <label className="adlbl" htmlFor="ds-alias">별칭 (쉼표로 구분)</label>
              <input id="ds-alias" className="adtools__in adform__in" value={form.aliases} onChange={setF('aliases')} />
              <label className="adlbl" htmlFor="ds-desc">설명</label>
              <textarea id="ds-desc" className="adtext" rows={3} value={form.description} onChange={setF('description')} />

              <p className="adlbl">추천 번호 (1~45, 출처 구분)</p>
              <div className="adnums">
                {form.numbers.map((n, i) => (
                  <button key={`${n.number}${n.origin}`} type="button" className="adnum adnum--x" onClick={() => dropNum(i)} title="눌러서 삭제">{n.number}<i>{ORIGINS[n.origin] || n.origin}</i> ×</button>
                ))}
              </div>
              <div className="adform__add">
                <input className="adtools__in" inputMode="numeric" placeholder="번호" value={newNum.number} onChange={(e) => setNewNum({ ...newNum, number: e.target.value })} />
                <select className="adtools__in" value={newNum.origin} onChange={(e) => setNewNum({ ...newNum, origin: e.target.value })} aria-label="번호 출처">
                  {Object.entries(ORIGINS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <button type="button" className="adbtn" onClick={addNum}>+ 추가</button>
              </div>

              {form.id ? (
                <>
                  <p className="adlbl">연결된 출처</p>
                  <ul className="addiff">{form.sources.map((x) => <li key={x.id}><b>{x.sourceName}</b><span>{x.memo || x.sourceType}</span></li>)}</ul>
                  <label className="adform__sw"><span>사용</span><button type="button" role="switch" aria-checked={form.active} className={`adswitch${form.active ? ' is-on' : ''}`} onClick={() => setForm((f) => ({ ...f, active: !f.active }))}><i /></button></label>
                </>
              ) : (
                <>
                  <label className="adlbl" htmlFor="ds-src">연결할 출처</label>
                  <select id="ds-src" className="adtools__in adform__in" value={form.sourceId} onChange={setF('sourceId')}>
                    <option value="">출처 선택</option>
                    {knownSources.map((x) => <option key={x.id} value={x.id}>{x.sourceName}</option>)}
                  </select>
                </>
              )}
              <div className="adact">
                <button type="button" className="adfilter__btn" onClick={save} disabled={busy}>{busy ? '저장 중…' : form.id ? '저장' : '등록'}</button>
                <button type="button" className="adbtn" onClick={() => setForm(null)}>취소</button>
              </div>
              {form.id && form.history.length > 0 && (
                <>
                  <p className="adlbl">수정 이력</p>
                  <ul className="addiff">{form.history.map((h) => <li key={h.id}><span>{dt(h.createdAt)}</span><b>{h.detail}</b></li>)}</ul>
                </>
              )}
            </>
          )}
        </aside>
      </div>
    </div>
  )
}
