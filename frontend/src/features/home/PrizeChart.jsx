import { useEffect, useRef, useState } from 'react'
import { formatEokValue } from '../../lib/format.js'

// 그래프 안쪽 여백(px): 왼쪽은 세로 눈금 글자, 아래는 회차 글자 자리
const PAD = { l: 42, r: 14, t: 18, b: 28 }

/** 세로 눈금 간격: 최댓값이 눈금 3칸 안팎에 들어오도록 보기 좋은 숫자(1, 2, 5, 10, 20 …)를 고른다 */
function niceStep(max) {
  const candidates = [1, 2, 5, 10, 20, 25, 50, 100, 200, 500, 1000]
  return candidates.find((c) => c >= max / 3) ?? 1000
}

/**
 * 1등 당첨금 추이 꺾은선 그래프. 글자가 늘어나 보이지 않도록 SVG 를 실제 칸 크기로 그린다.
 *  - points : [{ round, eok }]  회차 오름차순, eok 는 억 단위 숫자
 *  - 마우스를 올리거나(PC) 손가락으로 짚으면(모바일) 가까운 회차의 값을 말풍선으로 보여 주고,
 *    벗어나면 가장 최근 회차로 돌아온다.
 */
export default function PrizeChart({ points }) {
  const box = useRef(null)
  const [size, setSize] = useState({ w: 0, h: 0 })
  const [hover, setHover] = useState(null)

  // 칸 크기가 바뀌면(화면 폭 변경 등) 다시 그린다. 관찰을 시작하면 처음 크기도 한 번 알려 준다.
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

  const n = points.length
  const { w, h } = size
  const ready = w > 0 && h > 0 && n > 0
  const innerW = w - PAD.l - PAD.r
  const innerH = h - PAD.t - PAD.b

  let body = null
  if (ready) {
    const max = Math.max(...points.map((p) => p.eok))
    const step = niceStep(max)
    const top = Math.ceil(max / step) * step || step
    const ticks = []
    for (let v = 0; v <= top; v += step) ticks.push(v)

    const x = (i) => PAD.l + (n === 1 ? innerW / 2 : (innerW * i) / (n - 1))
    const y = (v) => PAD.t + innerH * (1 - v / top)
    const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.eok).toFixed(1)}`).join(' ')
    const area = `${line} L${x(n - 1).toFixed(1)},${y(0)} L${x(0).toFixed(1)},${y(0)} Z`

    const maxIndex = points.findIndex((p) => p.eok === max)
    const active = hover ?? n - 1
    const labelIdx = [...new Set([0, Math.round((n - 1) / 3), Math.round(((n - 1) * 2) / 3), n - 1])]

    const onMove = (e) => {
      const rect = box.current.getBoundingClientRect()
      const ratio = (e.clientX - rect.left - PAD.l) / innerW
      setHover(Math.min(n - 1, Math.max(0, Math.round(ratio * (n - 1)))))
    }

    const tipText = `${points[active].round}회 · ${formatEokValue(points[active].eok)}`
    // 말풍선이 칸 밖으로 잘리지 않게 좌우를 제한한다
    const tipLeft = Math.min(w - 64, Math.max(64, x(active)))

    body = (
      <>
        <svg
          width={w}
          height={h}
          role="img"
          aria-label={`1등 당첨금 추이 그래프. 최근 ${n}회, 최고 ${formatEokValue(max)}, 최근 ${formatEokValue(points[n - 1].eok)}`}
          key={`${points[0].round}-${points[n - 1].round}-${n}`}
        >
          <defs>
            <linearGradient id="prizeArea" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#7cc8fc" stopOpacity="0.38" />
              <stop offset="100%" stopColor="#7cc8fc" stopOpacity="0" />
            </linearGradient>
          </defs>
          {ticks.map((v) => (
            <g key={v}>
              <line x1={PAD.l} x2={w - PAD.r} y1={y(v)} y2={y(v)} className="chart__grid" />
              <text x={PAD.l - 8} y={y(v) + 4} textAnchor="end" className="chart__ylabel">
                {v === 0 ? '0' : `${v}억`}
              </text>
            </g>
          ))}
          <path d={area} fill="url(#prizeArea)" className="chart__area" />
          <path d={line} pathLength="1" className="chart__line" />
          {maxIndex !== active && <circle cx={x(maxIndex)} cy={y(max)} r="4.5" className="chart__dot chart__dot--max" />}
          <circle cx={x(active)} cy={y(points[active].eok)} r="5.5" className="chart__dot chart__dot--active" />
          {labelIdx.map((i) => (
            <text
              key={i}
              x={x(i)}
              y={h - 6}
              textAnchor="middle"
              className={`chart__xlabel${i === n - 1 ? ' is-last' : ''}`}
            >
              {points[i].round}
            </text>
          ))}
        </svg>
        <div className="chart__tip" style={{ left: tipLeft, top: y(points[active].eok) }}>
          {tipText}
        </div>
        <div
          className="chart__hit"
          onPointerMove={onMove}
          onPointerDown={onMove}
          onPointerLeave={() => setHover(null)}
          onPointerCancel={() => setHover(null)}
        />
      </>
    )
  }

  return (
    <div className="chart" ref={box}>
      {body}
    </div>
  )
}
