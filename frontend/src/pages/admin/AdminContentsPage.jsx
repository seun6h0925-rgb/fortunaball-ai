import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import Pagination from '../../components/ui/Pagination.jsx'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { CONTENT_TABS, NOTICE_PAGE_SIZE, NOTICE_TYPES, createNotice, deleteNotice, fetchAdminContents, fetchAdminNotices, updateContent, updateNotice } from '../../features/admin/contentsAdminApi.js'

const TYPE_NAMES = {
  SERVICE_INTRO: '서비스 소개', USAGE_GUIDE: '이용 방법', PRIVACY: '개인정보 안내', AI_NOTICE: 'AI 이용 안내',
  NO_GUARANTEE: '당첨 보장 없음 안내', GAMBLING_WARNING: '도박 중독 경고', DISCLAIMER: '면책 안내', CLAIM_GUIDE: '당첨금 수령 가이드', FAQ: '자주 묻는 질문',
}
const pad = (n) => String(n).padStart(2, '0')
const day = (s) => {
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`
}
const BLANK = { id: null, title: '', content: '', noticeType: 'GENERAL', visible: true }

export default function AdminContentsPage() {
  const [tab, setTab] = useState('notice-list')
  return (
    <div className="adcontents">
      <header className="adhead">
        <div><h1>콘텐츠 관리</h1><p>공지사항과 안내 문구를 고치면 사용자 화면에 반영돼요.</p></div>
      </header>
      <div className="card adfilter adfilter--tabs">
        <div className="adtabs adtabs--tight">
          <button type="button" className={`adtabs__b${tab === 'notice-list' ? ' is-on' : ''}`} onClick={() => setTab('notice-list')}>공지사항</button>
          {CONTENT_TABS.map((t) => <button key={t.key} type="button" className={`adtabs__b${tab === t.key ? ' is-on' : ''}`} onClick={() => setTab(t.key)}>{t.label}</button>)}
        </div>
      </div>
      {tab === 'notice-list' ? <NoticesTab /> : <ContentTab tab={CONTENT_TABS.find((t) => t.key === tab)} key={tab} />}
    </div>
  )
}

function NoticesTab() {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [form, setForm] = useState(null)
  const [busy, setBusy] = useState(false)
  const [confirmDel, setConfirmDel] = useState(false)
  const list = useApi((signal) => fetchAdminNotices(signal, page), [page])
  const items = list.data?.items || []
  const totalPages = Math.max(1, Math.ceil((list.data?.totalCount || 0) / NOTICE_PAGE_SIZE))
  const pick = (n) => { setForm({ id: n.noticeId, title: n.title, content: n.content, noticeType: n.noticeType, visible: n.visible }); setConfirmDel(false) }
  const setF = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const run = async (fn, okMsg, after) => {
    if (busy) return
    setBusy(true)
    try {
      await fn()
      toast(okMsg)
      after?.()
      list.reload()
    } catch (e) {
      toast(e?.message || '처리하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }
  const save = () => {
    if (!form.title.trim() || !form.content.trim()) { toast('제목과 내용을 모두 적어 주세요.'); return }
    const body = { title: form.title.trim(), content: form.content, noticeType: form.noticeType, visible: form.visible }
    if (form.id) run(() => updateNotice(form.id, body), '공지사항을 수정했어요.')
    else run(() => createNotice(body), '공지사항을 등록했어요.', () => setForm(null))
  }
  const toggleVisible = (n) => run(() => updateNotice(n.noticeId, { visible: !n.visible }), n.visible ? '비공개로 바꿨어요.' : '공개로 바꿨어요.')

  return (
    <div className="adsplit">
      <section className="card adpanel">
        <div className="adpanel__head"><h2>공지 목록</h2><button type="button" className="adfilter__btn" onClick={() => { setForm({ ...BLANK }); setConfirmDel(false) }}>공지 등록</button></div>
        {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
        {!list.data && list.loading && <Loading />}
        {list.data && items.length === 0 && <EmptyState title="등록된 공지가 없어요">오른쪽 위 "공지 등록"으로 첫 공지를 만들어 보세요.</EmptyState>}
        {items.length > 0 && (
          <table className="adtable adtable--click">
            <thead><tr><th>제목</th><th>구분</th><th>공개</th><th>수정일</th></tr></thead>
            <tbody>
              {items.map((n) => (
                <tr key={n.noticeId} className={n.noticeId === form?.id ? 'is-sel' : ''} onClick={() => pick(n)}>
                  <td>{n.title}</td>
                  <td><span className={`adpill${n.noticeType === 'IMPORTANT' ? ' is-bad' : ''}`}>{NOTICE_TYPES[n.noticeType] || n.noticeType}</span></td>
                  <td><button type="button" role="switch" aria-checked={n.visible} aria-label={`${n.title} 공개`} className={`adswitch${n.visible ? ' is-on' : ''}`} onClick={(e) => { e.stopPropagation(); toggleVisible(n) }} disabled={busy}><i /></button></td>
                  <td>{day(n.updatedAt || n.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={list.loading} />
      </section>

      <aside className="card adpanel">
        <div className="adpanel__head"><h2>{form?.id ? '공지 수정' : '공지 등록'}</h2></div>
        {!form ? (
          <p className="adempty">왼쪽에서 공지를 선택하거나 "공지 등록"을 눌러 주세요.</p>
        ) : (
          <>
            <label className="adlbl" htmlFor="nt-title">제목 (200자까지)</label>
            <input id="nt-title" className="adtools__in adform__in" maxLength={200} value={form.title} onChange={setF('title')} />
            <label className="adlbl" htmlFor="nt-body">내용 (5000자까지)</label>
            <textarea id="nt-body" className="adtext" rows={9} maxLength={5000} value={form.content} onChange={setF('content')} />
            <div className="adform__two adform__two--top">
              <label className="adform__sw"><span>공개</span><button type="button" role="switch" aria-checked={form.visible} className={`adswitch${form.visible ? ' is-on' : ''}`} onClick={() => setForm((f) => ({ ...f, visible: !f.visible }))}><i /></button></label>
              <select className="adtools__in" value={form.noticeType} onChange={setF('noticeType')} aria-label="구분">
                {Object.entries(NOTICE_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div className="adact">
              <button type="button" className="adfilter__btn" onClick={save} disabled={busy}>{busy ? '저장 중…' : form.id ? '저장' : '등록'}</button>
              <button type="button" className="adbtn" onClick={() => setForm(null)}>취소</button>
              {form.id && (confirmDel
                ? <button type="button" className="adbtn adbtn--bad" disabled={busy} onClick={() => run(() => deleteNotice(form.id), '공지사항을 삭제했어요.', () => { setForm(null); setConfirmDel(false) })}>정말 삭제</button>
                : <button type="button" className="adbtn adbtn--bad" onClick={() => setConfirmDel(true)}>삭제</button>)}
            </div>
          </>
        )}
      </aside>
    </div>
  )
}

function ContentTab({ tab }) {
  const toast = useToast()
  const all = useApi(fetchAdminContents, [])
  const [pick, setPick] = useState(null) // 고른 종류
  const [form, setForm] = useState(null)
  const [busy, setBusy] = useState(false)
  const rows = (all.data || []).filter((c) => tab.types.includes(c.contentType))
  const choose = (c) => { setPick(c.contentType); setForm({ title: c.title, content: c.content }) }
  const save = async () => {
    if (!pick || busy) return
    setBusy(true)
    try {
      await updateContent(pick, { title: form.title, content: form.content })
      toast('안내 내용을 수정했어요.')
      all.reload()
    } catch (e) {
      toast(e?.message || '저장하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="adsplit">
      <section className="card adpanel">
        <div className="adpanel__head"><h2>{tab.label}</h2></div>
        {all.error && <ErrorState error={all.error} onRetry={all.reload} />}
        {!all.data && all.loading && <Loading />}
        {all.data && rows.length === 0 && <EmptyState title="등록된 내용이 없어요">서버에 이 종류의 안내 문구가 아직 없어요.</EmptyState>}
        {rows.length > 0 && (
          <table className="adtable adtable--click">
            <thead><tr><th>종류</th><th>제목</th><th>수정일</th></tr></thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.contentType} className={c.contentType === pick ? 'is-sel' : ''} onClick={() => choose(c)}>
                  <td><b>{TYPE_NAMES[c.contentType] || c.contentType}</b></td>
                  <td>{c.title}</td>
                  <td>{c.updatedAt ? day(c.updatedAt) : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="adnote adnote--s">지금은 수령 가이드·서비스 안내 화면이 고정 문구라서, 여기서 고쳐도 사용자 화면에 바로 나오지 않는 곳이 있어요.</p>
      </section>

      <aside className="card adpanel">
        <div className="adpanel__head"><h2>{pick ? `${TYPE_NAMES[pick] || pick} 수정` : '안내 수정'}</h2></div>
        {!form ? (
          <p className="adempty">왼쪽에서 고칠 항목을 선택해 주세요.</p>
        ) : (
          <>
            <label className="adlbl" htmlFor="ct-title">제목</label>
            <input id="ct-title" className="adtools__in adform__in" value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
            <label className="adlbl" htmlFor="ct-body">내용 (마크다운)</label>
            <textarea id="ct-body" className="adtext" rows={14} value={form.content} onChange={(e) => setForm((f) => ({ ...f, content: e.target.value }))} />
            <div className="adact">
              <button type="button" className="adfilter__btn" onClick={save} disabled={busy}>{busy ? '저장 중…' : '저장'}</button>
              <button type="button" className="adbtn" onClick={() => { setForm(null); setPick(null) }}>취소</button>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}
