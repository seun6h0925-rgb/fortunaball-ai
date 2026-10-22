import { useState } from 'react'
import PageHeader, { ShareLikeBar } from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import { EmptyState, ErrorState } from '../components/ui/StateView.jsx'
import InfoTabs from '../features/info/InfoTabs.jsx'
import MapAiCard from '../features/map/MapAiCard.jsx'
import StoreMap from '../features/map/StoreMap.jsx'
import { ConsentSheet, GuideSheet, ListFoot, ListSheet, RookieCard, StoreList, StoreSheet, TopCard } from '../features/map/MapPanels.jsx'
import { PAGE_SIZE, PREVIEW_SIZE, RADII, fetchNearby, fetchRookies, radiusLabel, searchStores } from '../features/map/mapApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'
import { formatDrawDate } from '../lib/format.js'

/**
 * 로또 명당 지도(UI-11). 지역·판매점 검색(API-12), 내 위치 반경 조회(API-13), 상세(API-14), 루키 명당(API-66).
 *  - 내 위치: 스위치를 켜고 동의한 경우에만 쓴다. 위치는 이 화면의 메모리에만 두고 저장하지 않는다. (서버도 저장·로그 없음)
 *  - 지도는 StoreMap 이 맡는다. 지도 회사를 바꿀 때는 그 파일만 바꾼다.
 */
export default function MapPage() {
  usePageTitle('로또 명당 지도')
  const [text, setText] = useState('')
  const [keyword, setKeyword] = useState('')
  const [me, setMe] = useState(null) // { lat, lng } 내 위치 (동의 후에만)
  const [consented, setConsented] = useState(false) // 이번 방문 동안만 기억한다
  const [locating, setLocating] = useState(false)
  const [locError, setLocError] = useState('')
  const [radius, setRadius] = useState(1000)
  const [selected, setSelected] = useState(null)
  const [sheet, setSheet] = useState(null) // 'guide' | 'consent' | null
  const [listTab, setListTab] = useState(null) // 'result' | 'top' | 'rookie' (비우면 자동: 검색·위치 결과가 있으면 결과, 없으면 전국 명당)
  const [more, setMore] = useState(null) // 더보기 전체 목록 창: 'result' | 'top' | 'rookie' | null
  const [aiText, setAiText] = useState('') // 지도 AI 입력창 (사용법의 예시를 누르면 여기에 들어간다)

  // 검색어도 내 위치도 없으면(처음 화면) 전국 명당 TOP 만 보여 주고, 검색·내 위치를 쓰면 그 결과 목록을 따로 보여 준다
  const idle = !me && !keyword
  const list = useApi(
    (signal) => {
      if (idle) return Promise.resolve(null)
      // 내 주변은 서버가 반경 안을 한 번에 주므로 화면에서 나누고, 지역·이름 검색은 서버에서 페이지로 받는다
      if (me) return fetchNearby(signal, { lat: me.lat, lng: me.lng, radius })
      return searchStores(signal, { keyword, size: PREVIEW_SIZE })
    },
    [me, radius, keyword],
  )
  const top = useApi((signal) => searchStores(signal, { size: PREVIEW_SIZE }), [])
  const rookies = useApi((signal) => fetchRookies(signal), [])

  const raw = list.data?.items ?? []
  const topItems = top.data?.items ?? []
  // 내 주변은 서버가 가까운 순으로 주지만, 화면은 전국 순위와 같은 기준(1등+2등 합계 많은 순)으로 보여 준다
  const items = me ? [...raw].sort((a, b) => b.totalWins - a.totalWins || b.firstTotal - a.firstTotal || a.distanceMeters - b.distanceMeters) : raw
  // 카드에는 처음 10곳만 보인다 (내 주변은 서버가 반경 안을 한 번에 주므로 화면에서 자른다)
  const resTotal = me ? items.length : (list.data?.totalElements ?? 0)
  const resShown = me ? items.slice(0, PREVIEW_SIZE) : items
  const rkAll = rookies.data?.items ?? []
  const rkShown = rkAll.slice(0, PREVIEW_SIZE)
  const mapItems = idle ? topItems : resShown
  const picked = [...items, ...topItems].find((s) => s.storeId === selected) ?? rkAll.find((s) => s.storeId === selected)

  function locate() {
    if (!navigator.geolocation) {
      setLocError('이 기기에서는 위치를 쓸 수 없어요. 지역 검색으로 이용해 주세요.')
      return
    }
    setLocating(true)
    setLocError('')
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setMe({ lat: p.coords.latitude, lng: p.coords.longitude })
        setLocating(false)
      },
      () => {
        setLocating(false)
        setLocError('위치를 가져오지 못했어요. 브라우저의 위치 권한을 확인하거나 지역 검색으로 이용해 주세요.')
      },
      { timeout: 10000, maximumAge: 60000 },
    )
  }
  function onToggle() {
    setListTab(null)
    if (me || locating) {
      setMe(null)
      setLocating(false)
    } else if (consented) {
      locate()
    } else {
      setSheet('consent')
    }
  }
  function onSearch(e) {
    e.preventDefault()
    setMe(null) // 지역 검색을 하면 내 위치 보기는 끈다
    setListTab(null)
    setKeyword(text.trim())
  }

  const view = listTab === 'result' && idle ? 'top' : (listTab ?? (idle ? 'top' : 'result'))
  const title = me ? '가까운 당첨 판매점' : keyword ? `'${keyword}' 검색 결과` : '당첨 많은 판매점'

  return (
    <div className="mapp">
      <PageHeader title="로또 명당 지도" tagline share>
        <p className="phead__sub only-pc">지역별 당첨 판매점과 당첨 이력을 지도에서 찾아요</p>
        <InfoTabs />
      </PageHeader>

      <div className="mapp__bar">
      <form className="mapp__search" onSubmit={onSearch} role="search">
        <input className="mapp__input" type="search" placeholder="지역·판매점 검색" aria-label="지역 또는 판매점 검색"
          value={text} onChange={(e) => setText(e.target.value)} />
        <button type="submit" className="btn btn--cta">검색</button>
      </form>

      <div className="mapp__chips" role="group" aria-label="내 위치와 반경">
        <button type="button" role="switch" aria-checked={Boolean(me) || locating} className="mapp__switch" onClick={onToggle}>
          <span>내 위치</span>
          <i className={`mapp__knob${me || locating ? ' is-on' : ''}`} />
        </button>
        {RADII.map((r) => (
          <button key={r} type="button" className={`chip${r === radius ? ' is-on' : ''}`} aria-pressed={r === radius}
            onClick={() => setRadius(r)}>
            {radiusLabel(r)}
          </button>
        ))}
        <button type="button" className="chip" onClick={() => setSheet('guide')}>사용법</button>
      </div>
      </div>
      {locating && <p className="mapp__note" role="status">내 위치를 확인하고 있어요…</p>}
      {locError && <p className="gen__error" role="alert">{locError}</p>}

      <div className="mapp__grid">
        <div className="mapp__ai">
          <MapAiCard value={aiText} onChange={setAiText} me={me} onOpenGuide={() => setSheet('guide')} />
        </div>

        <section className="card card--hero smap__card smap__mapcard mapp__map" aria-label="지도">
          <StoreMap stores={mapItems} me={me} radius={radius} selectedId={selected} onSelect={setSelected} />
          <span className="smap__cap">마커 숫자는 1등 당첨 횟수</span>
        </section>

        <div className="mapp__side">
        <section className="card card--hero smap__card mapp__lists" aria-label="판매점 목록">
          <nav className="mapp__tabs" role="tablist" aria-label="판매점 목록 종류">
            {!idle && (
              <button type="button" role="tab" aria-selected={view === 'result'} className={`chip${view === 'result' ? ' is-on' : ''}`}
                onClick={() => setListTab('result')}>
                {me ? `내 주변 ${radiusLabel(radius)}` : '검색 결과'}
              </button>
            )}
            <button type="button" role="tab" aria-selected={view === 'top'} className={`chip${view === 'top' ? ' is-on' : ''}`}
              onClick={() => setListTab('top')}>전국 명당 TOP</button>
            <button type="button" role="tab" aria-selected={view === 'rookie'} className={`chip${view === 'rookie' ? ' is-on' : ''}`}
              onClick={() => setListTab('rookie')}>루키 명당</button>
          </nav>

          {view === 'result' && (
            <div className="smap__body">
              <p className="smap__desc">{title} · 1등+2등 합계가 많은 순이에요.</p>
              {list.loading && !list.data && <div className="skeleton" style={{ height: 180 }} aria-busy="true" />}
              {list.error && <ErrorState error={list.error} onRetry={list.reload} />}
              {list.data && resTotal === 0 && (
                <EmptyState title="판매점이 없어요">
                  <p>{me ? `반경 ${radiusLabel(radius)} 안에 당첨 판매점이 없어요. 반경을 넓혀 보세요.` : '다른 지역이나 이름으로 검색해 보세요.'}</p>
                </EmptyState>
              )}
              {resShown.length > 0 && <StoreList items={resShown} start={0} loading={list.loading} selected={selected} onPick={setSelected} />}
              <ListFoot total={resTotal} onOpen={() => setMore('result')} />
              {list.data?.baseDate && (
                <p className="smap__desc">
                  {list.data.statsFromRound ? `${list.data.statsFromRound}회 이후 기준 · ` : ''}데이터 기준일 {formatDrawDate(list.data.baseDate)}
                </p>
              )}
            </div>
          )}
          {view === 'top' && (
            <TopCard
              items={topItems}
              start={0}
              loading={top.loading}
              error={top.error}
              onRetry={top.reload}
              foot={
                <ListFoot total={top.data?.totalElements ?? 0} onOpen={() => setMore('top')} />
              }
              selected={selected}
              onPick={setSelected}
              statsFromRound={top.data?.statsFromRound}
              baseDate={top.data?.baseDate}
            />
          )}
          {view === 'rookie' && <RookieCard items={rkShown} onPick={setSelected} foot={
                <ListFoot total={rkAll.length} onOpen={() => setMore('rookie')} />
          } />}
        </section>
        </div>
      </div>

      <ShareLikeBar />
      <AdSlot />

      {more && (
        <ListSheet
          key={more}
          title={more === 'top' ? '전국 명당 TOP' : more === 'rookie' ? '루키 명당' : title}
          rookie={more === 'rookie'}
          selected={selected}
          onPick={setSelected}
          onClose={() => setMore(null)}
          load={async (signal, pg) => {
            const from = pg * PAGE_SIZE
            if (more === 'rookie') return { items: rkAll.slice(from, from + PAGE_SIZE), total: rkAll.length }
            if (more === 'result' && me) return { items: items.slice(from, from + PAGE_SIZE), total: items.length }
            const r = await searchStores(signal, { keyword: more === 'top' ? undefined : keyword, size: PAGE_SIZE, page: pg })
            return { items: r.items, total: r.totalElements }
          }}
        />
      )}
      {selected != null && picked && (
        <StoreSheet storeId={selected} distance={picked.distanceMeters} onClose={() => setSelected(null)} />
      )}
      {sheet === 'guide' && (
        <GuideSheet
          onClose={() => setSheet(null)}
          onPick={(t) => {
            setAiText(t)
            setSheet(null)
          }}
        />
      )}
      {sheet === 'consent' && (
        <ConsentSheet
          onClose={() => setSheet(null)}
          onAgree={() => {
            setConsented(true)
            setSheet(null)
            locate()
          }}
        />
      )}
    </div>
  )
}
