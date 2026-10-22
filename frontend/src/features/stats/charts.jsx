import { useEffect, useRef, useState } from 'react'

// 통계 분석 화면의 그래프 모음. 글자가 늘어나 보이지 않도록 SVG 는 실제 칸 크기로 그린다.

/** 칸 크기를 알려 주는 훅. 화면 폭이 바뀌면 다시 그린다. */
function useBoxSize() {
  const box = useRef(null)
  const [size, setSize] = useState({ w: 0, h: 0 })
  useEffect(() => {
    const el = box.current
    if (!el) return undefined
    const ro = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      setSize({ w: Math.round(width), h: Math.round(height) })
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  return [box, size]
}

/** 보기 좋은 눈금 간격 */
function niceStep(range) {
  const candidates = [1, 2, 5, 10, 20, 25, 50, 100, 200, 500]
  return candidates.find((c) => c >= range / 3) ?? 500
}

/**
 * 번호별 출현 횟수 막대 45개 (핫넘버). 많이 나온 5개는 밝게 표시한다.
 *  items: [{ number, count }], hot: 밝게 보일 번호 목록
 */
export function NumberBars({ items, hot }) {
  const [box, { w, h }] = useBoxSize()
  const PAD = { l: 34, r: 6, t: 14, b: 24 }
  const ready = w > 0 && h > 0 && items.length > 0
  let body = null
  if (ready) {
    const counts = items.map((i) => i.count)
    const max = Math.max(...counts)
    const min = Math.min(...counts)
    const step = niceStep(max - min || 1)
    const low = Math.floor(min / step) * step - step > 0 ? Math.floor(min / step) * step - step : 0
    const top = Math.ceil(max / step) * step
    const innerW = w - PAD.l - PAD.r
    const innerH = h - PAD.t - PAD.b
    const slot = innerW / items.length
    const barW = Math.max(2, slot * 0.62)
    const y = (v) => PAD.t + innerH * (1 - (v - low) / (top - low || 1))
    const ticks = []
    for (let v = low; v <= top; v += step) ticks.push(v)
    body = (
      <svg width={w} height={h} role="img" aria-label={`번호별 출현 횟수. 최다 ${max}회, 최소 ${min}회`}>
        {ticks.map((v) => (
          <g key={v}>
            <line x1={PAD.l} x2={w - PAD.r} y1={y(v)} y2={y(v)} className="chart__grid" />
            <text x={PAD.l - 6} y={y(v) + 4} textAnchor="end" className="chart__tick">
              {v}
            </text>
          </g>
        ))}
        {items.map((it, i) => {
          const x = PAD.l + slot * i + (slot - barW) / 2
          const yy = y(Math.max(it.count, low))
          const on = hot.includes(it.number)
          return (
            <rect key={it.number} x={x} y={yy} width={barW} height={Math.max(1, PAD.t + innerH - yy)} rx="1.5"
              className={on ? 'chart__bar chart__bar--hot' : 'chart__bar'}>
              <title>{`${it.number}번 ${it.count}회`}</title>
            </rect>
          )
        })}
        {[1, 15, 30, 45].map((n) => (
          <text key={n} x={PAD.l + slot * (n - 1) + slot / 2} y={h - 6} textAnchor="middle" className="chart__tick">
            {n}
          </text>
        ))}
      </svg>
    )
  }
  return (
    <div ref={box} className="chart chart--numbers">
      {body}
    </div>
  )
}

/** 도넛(비율 두 편). firstPercent 가 첫 번째 조각 */
export function Donut({ firstPercent, centerTop, centerBottom }) {
  const R = 46
  const C = 2 * Math.PI * R
  const first = Math.max(0, Math.min(100, firstPercent))
  return (
    <div className="donut" role="img" aria-label={`${centerBottom} ${first}%`}>
      <svg viewBox="0 0 120 120" width="100%" height="100%">
        <circle cx="60" cy="60" r={R} className="donut__b" />
        <circle cx="60" cy="60" r={R} className="donut__a" strokeDasharray={`${(C * first) / 100} ${C}`}
          transform="rotate(-90 60 60)" />
      </svg>
      <div className="donut__center">
        <b>{centerTop}</b>
        <span>{centerBottom}</span>
      </div>
    </div>
  )
}

/** 가로 막대 한 줄 목록. rows: [{ label, count }], 가장 큰 막대는 밝게 */
export function HBars({ rows, unit = '회' }) {
  const max = Math.max(1, ...rows.map((r) => r.count))
  const top = rows.findIndex((r) => r.count === max)
  return (
    <ul className="hbars">
      {rows.map((r, i) => (
        <li key={r.label} className="hbars__row">
          <span className="hbars__label">{r.label}</span>
          <span className="hbars__track">
            <span className={`hbars__fill${i === top ? ' is-top' : ''}`} style={{ width: `${(r.count / max) * 100}%` }} />
          </span>
          <span className="hbars__val">
            {r.count}
            {unit}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** 세로 막대(구간별 출현 횟수). rows: [{ label, count }] */
export function VBars({ rows }) {
  const max = Math.max(1, ...rows.map((r) => r.count))
  const top = rows.findIndex((r) => r.count === max)
  return (
    <div className="vbars" role="img" aria-label={rows.map((r) => `${r.label} ${r.count}회`).join(', ')}>
      {rows.map((r, i) => (
        <div key={r.label} className="vbars__col">
          <span className="vbars__val">{r.count}</span>
          <span className="vbars__bar">
            <span className={`vbars__fill${i === top ? ' is-top' : ''}`} style={{ height: `${(r.count / max) * 100}%` }} />
          </span>
          <span className="vbars__label">{r.label}</span>
        </div>
      ))}
    </div>
  )
}

/** 합계 꺾은선. series: [{ roundNo, sum }] 오래된 순 */
export function SumLine({ series }) {
  const [box, { w, h }] = useBoxSize()
  const PAD = { l: 34, r: 12, t: 14, b: 24 }
  const n = series.length
  const ready = w > 0 && h > 0 && n > 1
  let body = null
  if (ready) {
    const sums = series.map((s) => s.sum)
    const step = 25
    const low = Math.floor(Math.min(...sums) / step) * step
    const top = Math.ceil(Math.max(...sums) / step) * step
    const innerW = w - PAD.l - PAD.r
    const innerH = h - PAD.t - PAD.b
    const x = (i) => PAD.l + (innerW * i) / (n - 1)
    const y = (v) => PAD.t + innerH * (1 - (v - low) / (top - low || 1))
    const line = series.map((s, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(s.sum).toFixed(1)}`).join(' ')
    const area = `${line} L${x(n - 1)},${y(low)} L${x(0)},${y(low)} Z`
    const ticks = []
    for (let v = low; v <= top; v += step) ticks.push(v)
    const labelIdx = [...new Set([0, Math.round((n - 1) / 3), Math.round(((n - 1) * 2) / 3), n - 1])]
    body = (
      <svg width={w} height={h} role="img" aria-label={`회차별 당첨번호 합계. 최근 ${series[n - 1].sum}`}>
        <defs>
          <linearGradient id="sumArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#7cc8fc" stopOpacity="0.38" />
            <stop offset="100%" stopColor="#7cc8fc" stopOpacity="0" />
          </linearGradient>
        </defs>
        {ticks.map((v) => (
          <g key={v}>
            <line x1={PAD.l} x2={w - PAD.r} y1={y(v)} y2={y(v)} className="chart__grid" />
            <text x={PAD.l - 6} y={y(v) + 4} textAnchor="end" className="chart__tick">
              {v}
            </text>
          </g>
        ))}
        <path d={area} fill="url(#sumArea)" />
        <path d={line} className="chart__line" />
        <circle cx={x(n - 1)} cy={y(series[n - 1].sum)} r="4" className="chart__dot" />
        {labelIdx.map((i) => (
          <text key={i} x={x(i)} y={h - 6} textAnchor={i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle'} className="chart__tick">
            {series[i].roundNo}
          </text>
        ))}
      </svg>
    )
  }
  return (
    <div ref={box} className="chart chart--line">
      {body}
    </div>
  )
}
