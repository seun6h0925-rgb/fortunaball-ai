import { useState } from 'react'
import useApi from '../../hooks/useApi.js'
import Pagination from '../../components/ui/Pagination.jsx'
import { EmptyState, ErrorState, Loading } from '../../components/ui/StateView.jsx'
import StoreUploadPanel from '../../features/admin/StoreUploadPanel.jsx'
import { useToast } from '../../features/toast/toastContext.js'
import { STORE_PAGE_SIZE, fetchSidoNames, fetchStores, updateStore } from '../../features/admin/storesAdminApi.js'

const GEO = { PENDING: '변환 대기', SUCCESS: '정상', FAILED: '변환 실패' }
const EMPTY = { keyword: '', sido: '', geocodeStatus: '' }

export default function AdminStoresPage() {
  const toast = useToast()
  const [openTick, setOpenTick] = useState(0)
  const [draft, setDraft] = useState(EMPTY)
  const [applied, setApplied] = useState(EMPTY)
  const [page, setPage] = useState(0)
  const [pickId, setPickId] = useState(null)
  const [form, setForm] = useState(null) // { id, storeName, roadAddress, sido, sigungu, latitude, longitude }
  const [busy, setBusy] = useState(false)
  const sidos = useApi(fetchSidoNames, [])
  const list = useApi((signal) => fetchStores(signal, { ...applied, page }), [applied, page])

  const items = list.data?.items || []
  const totalPages = Math.max(1, Math.ceil((list.data?.totalCount || 0) / STORE_PAGE_SIZE))
  const set = (k) => (e) => setDraft((d) => ({ ...d, [k]: e.target.value }))
  const setF = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const submit = (e) => {
    e.preventDefault()
    setPage(0)
    setApplied({ ...draft, keyword: draft.keyword.trim() })
  }
  const pick = (s) => {
    setPickId(s.id)
    setForm({ id: s.id, storeName: s.storeName || '', roadAddress: s.roadAddress || '', sido: s.sido || '', sigungu: s.sigungu || '', latitude: s.latitude ?? '', longitude: s.longitude ?? '' })
  }
  const save = async () => {
    if (!form || busy) return
    const lat = form.latitude === '' ? null : Number(form.latitude)
    const lon = form.longitude === '' ? null : Number(form.longitude)
    if ((lat === null) !== (lon === null) || Number.isNaN(lat) || Number.isNaN(lon)) {
      toast('좌표는 위도와 경도를 숫자로 함께 적어 주세요.')
      return
    }
    setBusy(true)
    try {
      await updateStore(form.id, { storeName: form.storeName, roadAddress: form.roadAddress, sido: form.sido, sigungu: form.sigungu, latitude: lat, longitude: lon })
      toast('판매점 정보를 수정했어요.')
      list.reload()
    } catch (e) {
      toast(e?.message || '수정하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="adstores">
      <header className="adhead">
        <div>
          <h1>판매점 데이터 관리</h1>
          <p>공공데이터·동행복권 파일로 갱신하고, 좌표 변환에 실패한 곳은 직접 고쳐요.</p>
        </div>
        <button type="button" className="adfilter__btn" onClick={() => setOpenTick((n) => n + 1)}>CSV 등록</button>
      </header>

      <div className="adsplit">
        <div className="adstores__left">
          <form className="card adfilter" onSubmit={submit}>
            <input className="adtools__in adfilter__code" placeholder="판매점명·주소 검색" value={draft.keyword} onChange={set('keyword')} />
            <select className="adtools__in" value={draft.sido} onChange={set('sido')} aria-label="지역">
              <option value="">지역 전체</option>
              {(sidos.data || []).map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
            <select className="adtools__in" value={draft.geocodeStatus} onChange={set('geocodeStatus')} aria-label="좌표 상태">
              <option value="">좌표 전체</option>
              {Object.entries(GEO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <button type="submit" className="adfilter__btn">조회</button>
          </form>

          <section className="card adpanel">
            {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
            {!list.data && list.loading && <Loading />}
            {list.data && items.length === 0 && <EmptyState title="판매점이 없어요">조건을 바꿔서 다시 조회해 보세요.</EmptyState>}
            {items.length > 0 && (
              <table className="adtable adtable--click">
                <thead><tr><th>판매점명</th><th>주소</th><th>지역</th><th>좌표</th></tr></thead>
                <tbody>
                  {items.map((s) => (
                    <tr key={s.id} className={s.id === pickId ? 'is-sel' : ''} onClick={() => pick(s)}>
                      <td><b>{s.storeName}</b>{s.closed ? ' (폐점)' : ''}</td>
                      <td>{s.roadAddress || s.jibunAddress || '-'}</td>
                      <td>{s.sido}</td>
                      <td><span className={`adpill${s.geocodeStatus === 'SUCCESS' ? '' : ' is-bad'}`}>{GEO[s.geocodeStatus] || s.geocodeStatus}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <Pagination page={page} totalPages={totalPages} onChange={setPage} disabled={list.loading} />
          </section>
        </div>

        <div className="adstores__right">
          <section className="card adpanel">
            <div className="adpanel__head"><h2>판매점 수정</h2></div>
            {!form ? (
              <p className="adempty">왼쪽에서 판매점을 선택해 주세요.</p>
            ) : (
              <>
                <label className="adlbl" htmlFor="sf-name">판매점명</label>
                <input id="sf-name" className="adtools__in adform__in" value={form.storeName} onChange={setF('storeName')} />
                <label className="adlbl" htmlFor="sf-addr">주소 (도로명)</label>
                <input id="sf-addr" className="adtools__in adform__in" value={form.roadAddress} onChange={setF('roadAddress')} />
                <div className="adform__two">
                  <div><label className="adlbl" htmlFor="sf-sido">시·도</label><input id="sf-sido" className="adtools__in adform__in" value={form.sido} onChange={setF('sido')} /></div>
                  <div><label className="adlbl" htmlFor="sf-sgg">시·군·구</label><input id="sf-sgg" className="adtools__in adform__in" value={form.sigungu} onChange={setF('sigungu')} /></div>
                </div>
                <div className="adform__two">
                  <div><label className="adlbl" htmlFor="sf-lat">위도</label><input id="sf-lat" className="adtools__in adform__in" inputMode="decimal" value={form.latitude} onChange={setF('latitude')} /></div>
                  <div><label className="adlbl" htmlFor="sf-lon">경도</label><input id="sf-lon" className="adtools__in adform__in" inputMode="decimal" value={form.longitude} onChange={setF('longitude')} /></div>
                </div>
                <div className="adact">
                  <button type="button" className="adfilter__btn" onClick={save} disabled={busy}>{busy ? '저장 중…' : '저장'}</button>
                  <button type="button" className="adbtn" onClick={() => { setForm(null); setPickId(null) }}>취소</button>
                </div>
              </>
            )}
          </section>
          <StoreUploadPanel openTick={openTick} onApplied={list.reload} />
        </div>
      </div>
    </div>
  )
}
