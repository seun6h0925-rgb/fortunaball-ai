import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

/**
 * 지도 한 장. 이 파일만 지도 회사(지금은 OpenStreetMap, 이후 네이버 지도)에 의존한다.
 * 다른 지도로 바꿀 때는 같은 값(props)을 받는 이 파일만 새로 만들면 된다.
 *  - stores     : 마커로 표시할 판매점들 (마커 숫자 = 1등 당첨 횟수)
 *  - me         : { lat, lng } 내 위치 (없으면 표시하지 않는다)
 *  - radius     : 내 위치 반경(m). me 가 있을 때 원으로 보여 준다
 *  - selectedId : 눌려 있는 판매점
 *  - onSelect(storeId) : 마커를 눌렀을 때
 */
const KOREA = [36.35, 127.8]

export default function StoreMap({ stores, me, radius, selectedId, onSelect }) {
  const box = useRef(null)
  const mapRef = useRef(null)
  const layerRef = useRef(null)
  const pickRef = useRef(onSelect)

  useEffect(() => {
    pickRef.current = onSelect
  })

  // 지도 만들기 (화면이 열릴 때 한 번, 닫힐 때 정리)
  useEffect(() => {
    const map = L.map(box.current, { zoomControl: true }).setView(KOREA, 7)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap',
    }).addTo(map)
    layerRef.current = L.layerGroup().addTo(map)
    mapRef.current = map
    return () => {
      map.remove()
      mapRef.current = null
    }
  }, [])

  // 마커·내 위치 그리기
  useEffect(() => {
    const layer = layerRef.current
    if (!layer) return
    layer.clearLayers()
    stores.forEach((s) => {
      const cls = `smk${s.storeId === selectedId ? ' is-on' : ''}${s.rookie ? ' is-new' : ''}`
      const icon = L.divIcon({
        className: 'smk-wrap',
        html: `<span class="${cls}">${s.firstTotal}</span>`,
        iconSize: [34, 34],
        iconAnchor: [17, 17],
      })
      L.marker([s.latitude, s.longitude], { icon, title: s.name })
        .on('click', () => pickRef.current(s.storeId))
        .addTo(layer)
    })
    if (me) {
      L.circle([me.lat, me.lng], { radius, color: '#bfe3ff', weight: 2, dashArray: '6 6', fillOpacity: 0.08 }).addTo(layer)
      L.marker([me.lat, me.lng], {
        icon: L.divIcon({ className: 'smk-wrap', html: '<span class="smk-me"></span>', iconSize: [22, 22], iconAnchor: [11, 11] }),
        interactive: false,
      }).addTo(layer)
    }
  }, [stores, me, radius, selectedId])

  // 판매점이 바뀌면 모두 보이도록 맞춘다 (눌러서 강조만 바뀔 때는 움직이지 않는다)
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    if (me) {
      map.fitBounds(L.latLng(me.lat, me.lng).toBounds(radius * 2.4), { animate: false })
    } else if (stores.length > 0) {
      map.fitBounds(L.latLngBounds(stores.map((s) => [s.latitude, s.longitude])).pad(0.2), { maxZoom: 15, animate: false })
    }
  }, [stores, me, radius])

  return <div ref={box} className="smap" role="region" aria-label="판매점 지도" />
}
