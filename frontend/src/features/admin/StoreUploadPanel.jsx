import { useEffect, useRef, useState } from 'react'
import useApi from '../../hooks/useApi.js'
import { useToast } from '../toast/toastContext.js'
import { applyUpload, cancelUpload, fetchUploadDetail, fetchUploads, uploadStoreFile } from './storesAdminApi.js'

const nf = new Intl.NumberFormat('ko-KR')
const STATUS = { STAGED: '반영 대기', APPLIED: '반영 완료', FAILED: '실패', CANCELED: '취소' }
const KINDS = [['NEW', '신규'], ['CHANGED', '변경']]
const pad = (n) => String(n).padStart(2, '0')
const dt = (s) => {
  const d = new Date(s)
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 판매점 파일 올리기 → 변경점 확인 → 반영/취소 (연 1회 갱신용). onApplied: 반영 뒤 목록을 새로 받게 알려 준다. */
export default function StoreUploadPanel({ openTick, onApplied }) {
  const toast = useToast()
  const inputRef = useRef(null)
  const [current, setCurrent] = useState(null) // 방금 올린 업로드: { uploadId, fileName, validation, summary }
  const [kind, setKind] = useState('NEW')
  const [busy, setBusy] = useState('')
  const history = useApi(fetchUploads, [])
  const detail = useApi((signal) => (current ? fetchUploadDetail(signal, current.uploadId, kind) : Promise.resolve(null)), [current?.uploadId, kind])

  // 판매점 화면 위쪽 "CSV 등록" 버튼을 누를 때마다(openTick 증가) 파일 선택 창을 연다
  useEffect(() => {
    if (openTick) inputRef.current?.click()
  }, [openTick])

  const onFile = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setBusy('upload')
    try {
      const r = await uploadStoreFile(file)
      setCurrent(r)
      setKind('NEW')
      history.reload()
      toast('파일을 확인했어요. 변경점을 보고 반영해 주세요.')
    } catch (err) {
      toast(err?.message || '파일을 올리지 못했어요.')
    } finally {
      setBusy('')
    }
  }
  const run = async (name, fn, okMsg, after) => {
    if (busy) return
    setBusy(name)
    try {
      await fn(current.uploadId)
      toast(okMsg)
      after?.()
    } catch (err) {
      toast(err?.message || '처리하지 못했어요.')
    } finally {
      setBusy('')
    }
  }
  const s = current?.summary

  return (
    <section className="card adpanel">
      <div className="adpanel__head"><h2>판매점 파일 등록</h2></div>
      <input ref={inputRef} type="file" accept=".csv,text/csv" hidden onChange={onFile} />
      {!current && (
        <>
          <p className="adnote">동행복권 회차별 1·2등 당첨 판매점 CSV 파일을 올리면, 반영하기 전에 신규·변경 건수를 먼저 보여 줘요. 연 1회 갱신용이에요.</p>
          <button type="button" className="adbtn" onClick={() => inputRef.current?.click()} disabled={busy === 'upload'}>{busy === 'upload' ? '파일 확인 중…' : 'CSV 파일 선택'}</button>
        </>
      )}
      {current && (
        <>
          <p className="adnote">{current.fileName} · {current.validation.minRound}~{current.validation.maxRound}회 · 판매점 {nf.format(current.validation.storeCount)}곳</p>
          <div className="adminis">
            <div><span>신규 판매점</span><b>{nf.format(s.newStores ?? 0)}</b></div>
            <div><span>변경</span><b>{nf.format(s.changedStores ?? 0)}</b></div>
            <div><span>동일</span><b>{nf.format(s.unchangedStores ?? 0)}</b></div>
          </div>
          <p className="adnote adnote--s">당첨 이력: 새로 생김 {nf.format(s.newHistories ?? 0)}건 · 반영하면 지워지는 이력 {nf.format(s.removedHistories ?? 0)}건</p>
          <div className="adtabs">
            {KINDS.map(([k, label]) => <button key={k} type="button" className={`adtabs__b${kind === k ? ' is-on' : ''}`} onClick={() => setKind(k)}>{label}</button>)}
          </div>
          <ul className="addiff">
            {(detail.data?.items?.items || []).map((d) => (
              <li key={`${d.kind}${d.shopCode}`}>
                <b>{d.storeName}</b>
                <span>{Object.entries(d.after || {}).map(([k, v]) => `${k}: ${d.before?.[k] ? `${d.before[k]} → ` : ''}${v}`).join(' · ')}</span>
              </li>
            ))}
            {detail.data && (detail.data.items?.items || []).length === 0 && <li className="adempty">해당 항목이 없어요.</li>}
          </ul>
          <div className="adact">
            <button type="button" className="adfilter__btn" disabled={!!busy} onClick={() => run('apply', applyUpload, '판매점 데이터를 반영했어요.', () => { setCurrent(null); history.reload(); onApplied?.() })}>{busy === 'apply' ? '반영 중…' : '반영하기'}</button>
            <button type="button" className="adbtn" disabled={!!busy} onClick={() => run('cancel', cancelUpload, '업로드를 취소했어요.', () => { setCurrent(null); history.reload() })}>취소</button>
          </div>
        </>
      )}
      <p className="adlbl">최근 업로드</p>
      <ul className="addiff">
        {(history.data?.items || []).map((u) => (
          <li key={u.id}><b>{u.fileName}</b><span>{STATUS[u.status] || u.status} · {dt(u.uploadedAt)}</span></li>
        ))}
        {history.data && history.data.items.length === 0 && <li className="adempty">업로드 이력이 아직 없어요.</li>}
      </ul>
    </section>
  )
}
