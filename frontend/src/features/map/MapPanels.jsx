import { useEffect, useState } from 'react'
import Icon from '../../components/ui/Icon.jsx'
import Pagination from '../../components/ui/Pagination.jsx'
import { ErrorState } from '../../components/ui/StateView.jsx'
import useApi from '../../hooks/useApi.js'
import { formatDrawDate } from '../../lib/format.js'
import { useToast } from '../toast/toastContext.js'
import { PAGE_SIZE, PREVIEW_SIZE, distanceText, fetchStoreDetail, naverMapLink, winTypeText } from './mapApi.js'

// 로또 명당 지도(UI-11)의 카드와 창들.

const addressOf = (s) => s.roadAddress || s.jibunAddress || ''

/** 판매점 목록 한 줄 */
export function StoreRow({ rank, store, selected, onPick }) {
  return (
    <li>
      <button type="button" className={`srow${selected ? ' is-on' : ''}`} onClick={() => onPick(store.storeId)}>
        <span className="srow__no">{rank}</span>
        <span className="srow__main">
          <b>{store.name}</b>
          <small>{addressOf(store)}</small>
        </span>
        <span className="srow__stat">
          <b>1등 {store.firstTotal}회</b>
          <small>{store.distanceMeters != null ? `${distanceText(store.distanceMeters)} · ` : ''}2등 {store.second}회 · 합계 {store.totalWins}회</small>
        </span>
      </button>
    </li>
  )
}

/** 목록 아래 "더보기" 버튼: 누르면 전체 목록 창(ListSheet)이 열린다. 카드에는 처음 10곳만 보인다. */
export function ListFoot({ total, onOpen }) {
  if (total <= PREVIEW_SIZE) return null
  return (
    <button type="button" className="btn btn--ghost btn--block" onClick={onOpen}>
      더보기 ({PREVIEW_SIZE}/{total.toLocaleString('ko-KR')})
    </button>
  )
}

/**
 * 전체 목록 창: 50곳씩 한 페이지로 보여 주고 아래 1 2 3 4 로 넘긴다. (전국 명당·검색 결과·내 주변·루키 명당 공통)
 * load(signal, page) 는 { items, total } 을 돌려주는 함수(서버 페이지 또는 화면에서 자른 값).
 */
export function ListSheet({ title, load, rookie, selected, onPick, onClose }) {
  const [page, setPage] = useState(0)
  const q = useApi((signal) => load(signal, page), [page])
  const items = q.data?.items ?? []
  const total = q.data?.total ?? 0
  const start = page * PAGE_SIZE
  return (
    <Sheet title={title} labelId="mapl-title" onClose={onClose}>
      <p className="sheet__lead">총 {total.toLocaleString('ko-KR')}곳 · {total > 0 ? `${start + 1}~${start + items.length}번` : ''}</p>
      {q.loading && items.length === 0 && <div className="skeleton" style={{ height: 240 }} aria-busy="true" />}
      {q.error && <ErrorState error={q.error} onRetry={q.reload} />}
      {items.length > 0 && (rookie
        ? <RookieList items={items} onPick={onPick} />
        : <StoreList items={items} start={start} loading={q.loading} selected={selected} onPick={onPick} />)}
      <Pagination page={page} totalPages={Math.max(1, Math.ceil(total / PAGE_SIZE))} onChange={setPage} disabled={q.loading} />
    </Sheet>
  )
}

/** 판매점 목록 본문 (순위 번호는 start 부터). 전국 명당 TOP·검색 결과가 같이 쓴다. */
export function StoreList({ items, start = 0, loading, selected, onPick }) {
  return (
    <ul className={`slist${loading ? ' is-loading' : ''}`}>
      {items.map((s, i) => (
        <StoreRow key={s.storeId} rank={start + i + 1} store={s} selected={s.storeId === selected} onPick={onPick} />
      ))}
    </ul>
  )
}

/** 전국 명당 TOP: 지역·위치와 상관없이 전국 판매점을 총 당첨 횟수(1등+2등) 많은 순으로 보여 준다 (API-12 기본 정렬). */
export function TopCard({ items, start, loading, error, onRetry, selected, onPick, statsFromRound, baseDate, foot }) {
  return (
    <div className="smap__body" aria-label="전국 명당 TOP">
      <p className="smap__desc">전국 판매점을 1등+2등 합계가 많은 순으로 보여줘요.</p>
      {loading && items.length === 0 && <div className="skeleton" style={{ height: 180 }} aria-busy="true" />}
      {error && <ErrorState error={error} onRetry={onRetry} />}
      {items.length > 0 && <StoreList items={items} start={start} loading={loading} selected={selected} onPick={onPick} />}
      {foot}
      {(statsFromRound || baseDate) && (
        <p className="smap__desc">
          {statsFromRound ? `${statsFromRound}회 이후 기준` : ''}{statsFromRound && baseDate ? ' · ' : ''}{baseDate ? `데이터 기준일 ${formatDrawDate(baseDate)}` : ''}
        </p>
      )}
    </div>
  )
}

/** 루키 명당 목록 (카드·전체 목록 창 공통) */
function RookieList({ items, onPick }) {
  return (
    <ul className="slist">
          {items.map((s) => (
            <li key={s.storeId}>
              <button type="button" className="srow srow--rookie" onClick={() => onPick(s.storeId)}>
                <span className="srow__ico" aria-hidden="true"><Icon name="sparkle" size={16} /></span>
                <span className="srow__main">
                  <b>{s.name}</b>
                  <small>{addressOf(s)}</small>
                </span>
                <span className="srow__stat">
                  <b>{s.recentFirst > 0 ? `1등 ${s.recentFirst}회` : `2등 ${s.recentSecond}회`}</b>
                  <small>최근 12개월{s.recentFirst > 0 ? ` · 2등 ${s.recentSecond}회` : ''}</small>
                </span>
              </button>
            </li>
          ))}
    </ul>
  )
}

/** 루키 명당 카드 (최근 12개월 안에 1등 1회 이상 또는 2등 5회 이상 나온 판매점) */
export function RookieCard({ items, onPick, foot }) {
  return (
    <div className="smap__body" aria-label="루키 명당">
      <p className="smap__desc">최근 1년 기준으로 당첨이 새로 많이 나온 판매점이에요. (1등 1회 이상 또는 2등 5회 이상)</p>
      {items.length === 0 ? (
        <p className="smap__empty">지금은 기준에 맞는 판매점이 없어요.</p>
      ) : (
        <RookieList items={items} onPick={onPick} />
      )}
      {foot}
    </div>
  )
}

/** 창 공통 틀: 모바일은 아래 시트, PC 는 가운데 창. Esc·바깥 클릭·X 로 닫힌다. */
function Sheet({ title, labelId, onClose, children }) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="sheet" onClick={onClose}>
      <div className="sheet__box" role="dialog" aria-modal="true" aria-labelledby={labelId} onClick={(e) => e.stopPropagation()}>
        <span className="sheet__grip hide-pc" aria-hidden="true" />
        <div className="sheet__head">
          <h2 id={labelId} className="sheet__title">{title}</h2>
          <button type="button" className="icon-btn sheet__close" onClick={onClose} aria-label="닫기">
            <Icon name="close" size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

/** 판매점 상세 창(UI-11D): 주소 복사, 지도 앱으로 보기, 당첨 횟수, 회차별 당첨 이력 */
export function StoreSheet({ storeId, distance, onClose }) {
  const toast = useToast()
  const q = useApi((signal) => fetchStoreDetail(signal, storeId), [storeId])
  const store = q.data?.store

  async function copy() {
    try {
      await navigator.clipboard.writeText(addressOf(store))
      toast('주소를 복사했어요.')
    } catch {
      toast('복사하지 못했어요. 주소를 길게 눌러 직접 복사해 주세요.')
    }
  }

  return (
    <Sheet title={store ? store.name : '판매점 정보'} labelId="store-title" onClose={onClose}>
      {q.loading && !store && <div className="skeleton" style={{ height: 220 }} aria-busy="true" />}
      {q.error && <ErrorState error={q.error} onRetry={q.reload} />}
      {store && (
        <>
          <p className="sheet__lead">{addressOf(store)}{distance != null ? ` · 내 위치에서 ${distanceText(distance)}` : ''}</p>
          <div className="sstore__btns">
            <button type="button" className="btn btn--ghost" onClick={copy}>주소 복사</button>
            <a className="btn btn--cta" href={naverMapLink(store)} target="_blank" rel="noopener noreferrer">지도 앱으로 보기</a>
          </div>
          <div className="sstore__stats">
            <div><span>1등 당첨</span><b>{store.firstTotal}회</b></div>
            <div><span>2등 당첨</span><b>{store.second}회</b></div>
            <div><span>1등 자동</span><b>{store.firstAuto}회</b></div>
            <div><span>1등 수동</span><b>{store.firstManual}회</b></div>
          </div>
          {store.firstSemi > 0 && <p className="sstore__semi">1등 반자동 {store.firstSemi}회도 있어요.</p>}
          <h3 className="sheet__sub">당첨 이력</h3>
          {q.data.history.length === 0 ? (
            <p className="smap__empty">당첨 이력이 없어요.</p>
          ) : (
            <ul className="sstore__hist">
              {q.data.history.map((h) => (
                <li key={`${h.roundNo}-${h.rank}-${h.winType}`}>
                  <span>{h.roundNo}회</span>
                  <b>{h.rank}등</b>
                  <em>{winTypeText(h.winType)}</em>
                </li>
              ))}
            </ul>
          )}
          <p className="sheet__note">
            당첨 이력은 과거 기록이며 앞으로의 당첨과 무관해요.
            {q.data.statsFromRound ? ` ${q.data.statsFromRound}회 이후 기준` : ''}
            {q.data.baseDate ? ` · 데이터 기준일 ${formatDrawDate(q.data.baseDate)}` : ''}
          </p>
        </>
      )}
    </Sheet>
  )
}

/** 지도 사용법 창(UI-11B) */
const GUIDE = [
  ['지역으로 찾기', '검색창에 지역이나 판매점 이름을 입력하면 지도가 이동하고 판매점 마커가 표시돼요.'],
  ['내 위치로 찾기', '"내 위치"를 누르고 동의하면 반경(300m·500m·1km) 안의 판매점을 찾아 줘요. 위치는 저장되지 않아요.'],
  ['마커·목록 눌러 보기', '마커 숫자는 1등 당첨 횟수예요. 판매점을 누르면 주소, 당첨 이력, 지도 연결이 나와요.'],
  ['주소 복사·길 찾기', '상세 화면에서 주소를 복사하거나 지도 앱으로 연결할 수 있어요.'],
  ['1km보다 멀리 찾기', '지도를 직접 옮기거나, 주소를 검색해 그 주소 기준으로 주변을 찾거나, 지도 AI에게 물어보세요.'],
]
const AI_GUIDE = [
  ['AI에게 물어보기', '여기서 가까운 곳 중 당첨 제일 많은 판매점 알려줘', '지도 위 입력창에 질문을 적으면 AI가 조건에 맞는 판매점을 찾아 안내해 줘요.'],
  ['지역 조건 넣기', '반경 1km 안에서 1등 많이 나온 곳', '지역이나 주소, 반경(300m·500m·1km)을 함께 말해도 돼요. 1km보다 먼 곳은 지역이나 주소를 알려 주세요.'],
  ['위치 동의', null, '가까운 곳을 찾으려면 위치 동의가 필요해요. 위치는 저장하지 않고 AI에도 전달하지 않아요.'],
  ['답변과 지도 연결', null, 'AI 답변의 판매점은 서버에 등록된 정보만 안내해요. 목록의 판매점을 누르면 상세가 열려요.'],
]
export function GuideSheet({ onClose, onPick }) {
  const [tab, setTab] = useState('map')
  return (
    <Sheet title="사용법" labelId="map-guide-title" onClose={onClose}>
      <div className="mapg__tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'map'} className={`chip${tab === 'map' ? ' is-on' : ''}`} onClick={() => setTab('map')}>지도 사용법</button>
        <button type="button" role="tab" aria-selected={tab === 'ai'} className={`chip${tab === 'ai' ? ' is-on' : ''}`} onClick={() => setTab('ai')}>지도 AI 사용법</button>
      </div>
      {tab === 'map' ? (
        <>
          <ol className="guide">
            {GUIDE.map(([t, d], i) => (
              <li key={t} className="guide__item">
                <span className="guide__no" aria-hidden="true">{i + 1}</span>
                <div className="guide__main">
                  <b className="guide__title">{t}</b>
                  <p className="guide__desc">{d}</p>
                </div>
              </li>
            ))}
          </ol>
          <p className="sheet__note">당첨 횟수는 과거 기록이며 앞으로의 당첨과 무관해요.</p>
        </>
      ) : (
        <>
          <ol className="guide">
            {AI_GUIDE.map(([t, ex, d], i) => (
              <li key={t} className="guide__item">
                <span className="guide__no" aria-hidden="true">{i + 1}</span>
                <div className="guide__main">
                  <b className="guide__title">{t}</b>
                  {ex && <button type="button" className="guide__example" onClick={() => onPick(ex)}>{ex}</button>}
                  <p className="guide__desc">{d}</p>
                </div>
              </li>
            ))}
          </ol>
          <p className="sheet__note">
            AI 질문은 애널리스트·꿈풀이와 같은 무료 횟수를 함께 써요(질문 1번에 1회 차감, 오늘 남은 횟수 표시). 답변은 500자 이내이고, 판매점·명당·당첨 이력 질문에만 답해요. 맛집 같은 관련 없는 질문은 안내 문구로 돌려드려요. 통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.
          </p>
        </>
      )}
    </Sheet>
  )
}

/** 위치 동의 창(UI-11E). 동의하면 이어서 브라우저의 위치 권한 창이 나온다. */
export function ConsentSheet({ onAgree, onClose }) {
  return (
    <Sheet title="내 위치를 사용할까요?" labelId="consent-title" onClose={onClose}>
      <p className="sheet__lead">
        현재 위치를 지도에 표시하고, 반경 300m·500m·1km 안의 당첨 판매점을 찾는 데만 써요. AI에게 "내 위치 기준"으로 물어볼 때도 같은 용도로 써요.
      </p>
      <ul className="gdet__list">
        <li>위치는 저장하거나 기록하지 않아요.</li>
        <li>AI에는 위치 대신 조회된 판매점 목록만 전달해요.</li>
        <li>스위치를 끄면 지도에서 위치가 바로 사라져요.</li>
        <li>동의하면 이어서 브라우저의 위치 권한 창이 나와요.</li>
      </ul>
      <div className="sstore__btns">
        <button type="button" className="btn btn--ghost" onClick={onClose}>거절</button>
        <button type="button" className="btn btn--cta" onClick={onAgree}>동의</button>
      </div>
      <p className="sheet__note">거절하면 지역 검색으로 이용할 수 있어요.</p>
    </Sheet>
  )
}
