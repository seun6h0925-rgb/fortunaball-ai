import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import Icon from '../../components/ui/Icon.jsx'
import Pagination from '../../components/ui/Pagination.jsx'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { fetchPendingCount, fetchVerifications, photoSrc, reviewVerification } from '../../features/admin/photosAdminApi.js'

const TABS = [['PENDING', '대기'], ['APPROVED', '승인'], ['REJECTED', '반려'], ['HIDDEN', '비공개']]
const LABEL = { PENDING: '검수 대기', APPROVED: '승인', REJECTED: '반려', HIDDEN: '비공개' }
const REASONS = ['사진이 흐리거나 잘려서 확인할 수 없어요.', '발급코드와 사진의 용지가 달라요.', '개인정보가 사진에 보여요.', '부적절한 댓글이 있어요.']
const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function AdminPhotosPage() {
  const toast = useToast()
  const [tab, setTab] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [pickId, setPickId] = useState(null)
  const [notes, setNotes] = useState({})
  const [busy, setBusy] = useState(false)
  const [photoFail, setPhotoFail] = useState({})
  const pending = useApi(fetchPendingCount, [])
  const list = useApi((signal) => fetchVerifications(signal, { status: tab, page }), [tab, page])

  const items = list.data?.items || []
  const picked = items.find((i) => i.id === pickId) || items[0]
  const note = picked ? notes[picked.id] ?? picked.adminComment ?? '' : ''
  const totalPages = Math.max(1, list.data?.totalPages || 1)

  const act = async (status) => {
    if (!picked || busy) return
    if (status !== 'APPROVED' && !note.trim()) {
      toast('반려·비공개는 사유(관리자 코멘트)를 꼭 적어 주세요.')
      return
    }
    setBusy(true)
    try {
      await reviewVerification(picked.id, status, note.trim() || null)
      toast(status === 'APPROVED' ? '승인했어요. 사용자 화면에 공개돼요.' : status === 'REJECTED' ? '반려했어요.' : '비공개로 처리했어요.')
      setPickId(null)
      list.reload()
      pending.reload()
    } catch (e) {
      toast(e?.message || '처리하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="adphotos">
      <header className="adhead">
        <div>
          <h1>익명 당첨 인증 검수</h1>
          <p>사진은 비공개 저장소의 임시 주소로만 열려요.</p>
        </div>
      </header>

      <div className="adphotos__grid">
        <section className="card adpanel">
          <div className="adtabs">
            {TABS.map(([k, label]) => (
              <button key={k} type="button" className={`adtabs__b${tab === k ? ' is-on' : ''}`} onClick={() => { setTab(k); setPage(0); setPickId(null) }}>
                {label}{k === 'PENDING' && pending.data != null ? ` ${pending.data}` : ''}
              </button>
            ))}
          </div>
          {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
          {!list.data && list.loading && <Loading />}
          {list.data && items.length === 0 && <EmptyState title="해당하는 인증이 없어요">{tab === 'PENDING' ? '검수할 인증이 들어오면 여기에 나와요.' : '이 상태의 인증이 아직 없어요.'}</EmptyState>}
          <div className="adq">
            {items.map((i) => (
              <button key={i.id} type="button" className={`adq__row${i.id === picked?.id ? ' is-sel' : ''}`} onClick={() => setPickId(i.id)}>
                <span><b>{i.roundNo}회{i.bestRank ? ` · ${i.bestRank}등` : ''}</b><em>{dt(i.createdAt)}</em></span>
                <span>{i.issueCode}</span>
              </button>
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={list.loading} />
        </section>

        <section className="card adpanel">
          <div className="adpanel__head"><h2>검수 상세</h2>{picked && <span className="adpill">{LABEL[picked.reviewStatus] || picked.reviewStatus}</span>}</div>
          {!picked ? (
            <p className="adempty">왼쪽에서 검수할 인증을 선택해 주세요.</p>
          ) : (
            <>
              <div className="adphotos__body">
                <a className="adphoto" href={photoSrc(picked.id)} target="_blank" rel="noreferrer" title="새 창에서 크게 보기">
                  {photoFail[picked.id] ? (
                    <span className="adphoto__ph"><Icon name="image" size={34} />사진을 불러오지 못했어요</span>
                  ) : (
                    <img src={photoSrc(picked.id)} alt="인증 사진" onError={() => setPhotoFail((m) => ({ ...m, [picked.id]: true }))} />
                  )}
                </a>
                <div className="adphotos__side">
                  <div className="adbox">
                    <b>발급코드 검증 결과</b>
                    <p>코드 {picked.issueCode}</p>
                    <p>회차 {picked.roundNo}회 · 등수 {picked.bestRank ? `${picked.bestRank}등` : '검증 전'} {picked.bestRank && <span className="adpill">일치</span>}</p>
                  </div>
                  <p className="adlbl">사용자 댓글</p>
                  <div className="adbox adbox--soft">{picked.comment || '댓글이 없어요.'}</div>
                  <p className="adlbl">관리자 코멘트 (승인 시 공개돼요 · 반려·비공개는 사유 필수)</p>
                  <textarea className="adtext" rows={3} maxLength={500} value={note} onChange={(e) => setNotes((m) => ({ ...m, [picked.id]: e.target.value }))} placeholder="승인 시 함께 공개돼요" />
                </div>
              </div>
              <div className="adact">
                <button type="button" className="adfilter__btn" onClick={() => act('APPROVED')} disabled={busy}>승인</button>
                <button type="button" className="adbtn adbtn--bad" onClick={() => act('REJECTED')} disabled={busy}>반려</button>
                <button type="button" className="adbtn" onClick={() => act('HIDDEN')} disabled={busy}>비공개 처리</button>
                <select className="adtools__in adact__sel" value="" onChange={(e) => e.target.value && setNotes((m) => ({ ...m, [picked.id]: e.target.value }))} aria-label="사유 선택">
                  <option value="">사유 선택</option>
                  {REASONS.map((r) => <option key={r} value={r}>{r}</option>)}
                </select>
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  )
}
