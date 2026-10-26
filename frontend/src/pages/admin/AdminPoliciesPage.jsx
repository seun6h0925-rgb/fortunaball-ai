import { useMemo, useState } from 'react'
import useApi from '../../hooks/useApi.js'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { MATCH, SCOPES, createPolicy, fetchPolicies, updatePolicy } from '../../features/admin/policiesAdminApi.js'

const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const BLANK = { id: null, policyCode: '', policyName: '', featureScope: 'ALL', policyType: '', actionType: 'FIXED_RESPONSE', priority: '50', responseMessage: '', keywords: [], active: true }
const toForm = (p) => ({ id: p.id, policyCode: p.policyCode, policyName: p.policyName, featureScope: p.featureScope, policyType: p.policyType, actionType: p.actionType, priority: String(p.priority), responseMessage: p.responseMessage || '', keywords: p.keywords.map((k) => ({ keyword: k.keyword, matchType: k.matchType, active: k.active })), active: p.active })
const kwBody = (ks) => ks.map((k) => ({ keyword: k.keyword, matchType: k.matchType, active: k.active }))

export default function AdminPoliciesPage() {
  const toast = useToast()
  const [scope, setScope] = useState('')
  const [activeF, setActiveF] = useState('')
  const [form, setForm] = useState(null)
  const [kw, setKw] = useState({ keyword: '', matchType: 'CONTAINS' })
  const [busy, setBusy] = useState(false)
  const list = useApi(fetchPolicies, [])

  const all = useMemo(() => list.data || [], [list.data])
  const rows = all
    .filter((p) => !scope || p.featureScope === scope)
    .filter((p) => activeF === '' || String(p.active) === activeF)
    .sort((a, b) => a.priority - b.priority)
  const kwTotal = all.reduce((n, p) => n + p.keywords.length, 0)
  const kwOn = all.reduce((n, p) => n + p.keywords.filter((k) => k.active).length, 0)
  const latest = all.reduce((m, p) => (p.updatedAt && (!m || p.updatedAt > m.updatedAt) ? p : m), null)
  const setF = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const addKw = () => {
    const k = kw.keyword.trim()
    if (!k) return
    if (form.keywords.some((x) => x.keyword === k)) { toast('이미 있는 키워드예요.'); return }
    setForm((f) => ({ ...f, keywords: [...f.keywords, { keyword: k, matchType: kw.matchType, active: true }] }))
    setKw({ ...kw, keyword: '' })
  }
  const flipKw = (i) => setForm((f) => ({ ...f, keywords: f.keywords.map((x, n) => (n === i ? { ...x, active: !x.active } : x)) }))
  const dropKw = (i) => setForm((f) => ({ ...f, keywords: f.keywords.filter((_, n) => n !== i) }))

  const save = async () => {
    if (!form || busy) return
    const priority = Number(form.priority)
    if (!Number.isInteger(priority) || priority < 0) { toast('우선순위는 0 이상의 숫자로 적어 주세요.'); return }
    setBusy(true)
    try {
      if (form.id) {
        await updatePolicy(form.id, { policyName: form.policyName, priority, responseMessage: form.responseMessage, keywords: kwBody(form.keywords) })
        toast('정책을 수정했어요. 서비스에는 최대 1분 안에 반영돼요.')
      } else {
        if (!form.policyCode.trim() || !form.policyName.trim() || !form.policyType.trim()) { toast('정책 코드, 이름, 유형을 모두 적어 주세요.'); setBusy(false); return }
        await createPolicy({ policyCode: form.policyCode.trim().toUpperCase(), policyName: form.policyName.trim(), featureScope: form.featureScope, policyType: form.policyType.trim().toUpperCase(), actionType: form.actionType, priority, responseMessage: form.responseMessage, keywords: kwBody(form.keywords) })
        toast('정책을 등록했어요. 서비스에는 최대 1분 안에 반영돼요.')
        setForm(null)
      }
      list.reload()
    } catch (e) {
      toast(e?.message || '저장하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }
  const toggle = async (p) => {
    if (busy) return
    setBusy(true)
    try {
      await updatePolicy(p.id, { active: !p.active })
      toast(p.active ? '정책을 껐어요.' : '정책을 켰어요.')
      list.reload()
    } catch (e) {
      toast(e?.message || '바꾸지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="adpol">
      <header className="adhead">
        <div><h1>AI 입력 정책 관리</h1><p>질문에 키워드가 들어 있으면 AI 대신 고정 문구로 답해요. 바뀐 내용은 서비스에 최대 1분 안에 반영돼요.</p></div>
        <button type="button" className="adfilter__btn" onClick={() => setForm({ ...BLANK })}>정책 등록</button>
      </header>

      <div className="adstats">
        <div className="card adstat"><p className="adstat__label">정책</p><p className="adstat__value">{all.length}개</p><p className="adstat__sub">사용 중 {all.filter((p) => p.active).length} · 꺼짐 {all.filter((p) => !p.active).length}</p></div>
        <div className="card adstat"><p className="adstat__label">키워드</p><p className="adstat__value">{kwTotal}개</p><p className="adstat__sub">사용 중 {kwOn}개</p></div>
        <div className="card adstat"><p className="adstat__label">고정 응답 방식</p><p className="adstat__value adstat__value--s">FIXED_RESPONSE</p><p className="adstat__sub">AI 검토 방식은 아직 서비스에서 쓰이지 않아요</p></div>
        <div className="card adstat"><p className="adstat__label">최근 변경</p><p className="adstat__value adstat__value--s">{latest ? dt(latest.updatedAt) : '-'}</p><p className="adstat__sub">{latest ? latest.policyName : ' '}</p></div>
      </div>

      <div className="card adfilter adfilter--tabs">
        <div className="adtabs adtabs--tight">
          <button type="button" className={`adtabs__b${scope === '' ? ' is-on' : ''}`} onClick={() => setScope('')}>전체</button>
          {SCOPES.map((s) => <button key={s} type="button" className={`adtabs__b${scope === s ? ' is-on' : ''}`} onClick={() => setScope(s)}>{s}</button>)}
        </div>
        <select className="adtools__in adfilter__right" value={activeF} onChange={(e) => setActiveF(e.target.value)} aria-label="사용 여부">
          <option value="">사용 여부 전체</option>
          <option value="true">사용 중</option>
          <option value="false">꺼짐</option>
        </select>
      </div>

      <div className="adsplit">
        <section className="card adpanel">
          {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
          {!list.data && list.loading && <Loading />}
          {list.data && rows.length === 0 && <EmptyState title="정책이 없어요">조건을 바꾸거나 정책을 등록해 주세요.</EmptyState>}
          {rows.length > 0 && (
            <table className="adtable adtable--click">
              <thead><tr><th>정책 코드 · 이름</th><th>유형</th><th>범위</th><th>우선순위</th><th>사용</th></tr></thead>
              <tbody>
                {rows.map((p) => (
                  <tr key={p.id} className={p.id === form?.id ? 'is-sel' : ''} onClick={() => setForm(toForm(p))}>
                    <td><b className="admono">{p.policyCode}</b><br /><span className="adalias">{p.policyName}</span></td>
                    <td>{p.policyType}</td>
                    <td><span className="adpill">{p.featureScope}</span></td>
                    <td>{p.priority}</td>
                    <td><button type="button" role="switch" aria-checked={p.active} aria-label={`${p.policyCode} 사용`} className={`adswitch${p.active ? ' is-on' : ''}`} onClick={(e) => { e.stopPropagation(); toggle(p) }} disabled={busy}><i /></button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p className="adnote adnote--s">우선순위 숫자가 작을수록 먼저 검사해요. 정책 코드·범위·유형·처리 방식은 등록할 때만 정할 수 있어요.</p>
        </section>

        <aside className="card adpanel">
          <div className="adpanel__head"><h2>{!form ? '정책 수정' : form.id ? `${form.policyCode} 수정` : '정책 등록'}</h2></div>
          {!form ? (
            <p className="adempty">왼쪽에서 정책을 선택하거나 "정책 등록"을 눌러 주세요.</p>
          ) : (
            <>
              {!form.id && (
                <>
                  <label className="adlbl" htmlFor="pl-code">정책 코드 (영문 대문자·밑줄)</label>
                  <input id="pl-code" className="adtools__in adform__in" value={form.policyCode} onChange={setF('policyCode')} />
                  <div className="adform__two">
                    <div><label className="adlbl" htmlFor="pl-scope">기능 범위</label><select id="pl-scope" className="adtools__in adform__in" value={form.featureScope} onChange={setF('featureScope')}>{SCOPES.map((s) => <option key={s}>{s}</option>)}</select></div>
                    <div><label className="adlbl" htmlFor="pl-type">유형</label><input id="pl-type" className="adtools__in adform__in" value={form.policyType} onChange={setF('policyType')} placeholder="예: OFF_TOPIC" /></div>
                  </div>
                </>
              )}
              <label className="adlbl" htmlFor="pl-msg">고정 응답 문구</label>
              <textarea id="pl-msg" className="adtext" rows={4} value={form.responseMessage} onChange={setF('responseMessage')} />
              <div className="adform__two">
                <div><label className="adlbl" htmlFor="pl-pri">우선순위</label><input id="pl-pri" className="adtools__in adform__in" inputMode="numeric" value={form.priority} onChange={setF('priority')} /></div>
                <div><label className="adlbl" htmlFor="pl-name">이름</label><input id="pl-name" className="adtools__in adform__in" value={form.policyName} onChange={setF('policyName')} /></div>
              </div>

              <p className="adlbl">키워드 (눌러서 켜고 끄고, × 로 삭제)</p>
              <div className="adnums">
                {form.keywords.map((k, i) => (
                  <span key={k.keyword} className={`adkw${k.active ? '' : ' is-off'}`}>
                    <button type="button" onClick={() => flipKw(i)}>{k.keyword} · {MATCH[k.matchType] || k.matchType}{k.active ? '' : ' · 꺼짐'}</button>
                    <button type="button" aria-label={`${k.keyword} 삭제`} onClick={() => dropKw(i)}>×</button>
                  </span>
                ))}
              </div>
              <div className="adform__add adform__add--kw">
                <input className="adtools__in" placeholder="키워드 추가" value={kw.keyword} onChange={(e) => setKw({ ...kw, keyword: e.target.value })} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addKw() } }} />
                <select className="adtools__in" value={kw.matchType} onChange={(e) => setKw({ ...kw, matchType: e.target.value })} aria-label="일치 방식">
                  {Object.entries(MATCH).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <button type="button" className="adbtn" onClick={addKw}>추가</button>
              </div>
              <p className="adnote adnote--s">키워드 목록을 저장하면 목록에 없는 기존 키워드는 삭제돼요. 잠깐 끄려면 키워드를 눌러 "꺼짐"으로 바꿔 주세요.</p>
              <div className="adact">
                <button type="button" className="adbtn" onClick={() => setForm(null)}>취소</button>
                <button type="button" className="adfilter__btn" onClick={save} disabled={busy}>{busy ? '저장 중…' : form.id ? '저장' : '등록'}</button>
              </div>
            </>
          )}
        </aside>
      </div>
    </div>
  )
}
