import { useEffect, useRef, useState } from 'react'

/**
 * 회차 고르기 공용 부품. 회차를 고르는 모든 화면(통계 분석·회차별 당첨번호·명당 지도 등)이 이것 하나를 쓴다.
 *  - 양옆 ‹ › 화살표: 한 회차씩 앞뒤로 이동 (맨 처음/최신에서는 막힘)
 *  - 가운데 회차를 누르면 선택 창이 열린다: ① 회차 숫자 직접 입력 ② 날짜로 고르기 ③ 목록을 스크롤해서 고르기
 * 값(value)과 바꾸는 방법(onChange)은 화면이 들고 있고, 이 부품은 고른 회차 번호만 돌려준다.
 */

// 1회 추첨일 2002-12-07(토). 이후 매주 토요일에 1회씩 → 날짜와 회차를 서로 계산할 수 있다.
const FIRST_DRAW = Date.UTC(2002, 11, 7)
const DAY = 86400000

const roundToDate = (r) => new Date(FIRST_DRAW + (r - 1) * 7 * DAY).toISOString().slice(0, 10)
/** 고른 날짜까지 가장 가까운 추첨 회차 (그 날짜 이전/당일 토요일) */
const dateToRound = (iso) => {
  const t = Date.parse(`${iso}T00:00:00Z`)
  return Number.isNaN(t) ? null : Math.floor((t - FIRST_DRAW) / (7 * DAY)) + 1
}

export default function RoundNav({ value, latest, onChange, first = 1 }) {
  const [open, setOpen] = useState(false)
  if (value == null || latest == null) return null
  const go = (r) => onChange(Math.min(latest, Math.max(first, r)))
  return (
    <>
      <div className="rnav" role="group" aria-label="회차 이동">
        <button type="button" className="rnav__arrow" aria-label="이전 회차" disabled={value <= first}
          onClick={() => go(value - 1)}>
          ‹
        </button>
        <button type="button" className="rnav__center" aria-haspopup="dialog" onClick={() => setOpen(true)}>
          <b>{value}회</b>
          {value === latest && <span className="rnav__tag">최신</span>}
          <span className="rnav__caret" aria-hidden="true">▾</span>
        </button>
        <button type="button" className="rnav__arrow" aria-label="다음 회차" disabled={value >= latest}
          onClick={() => go(value + 1)}>
          ›
        </button>
      </div>
      {open && (
        <RoundSheet
          value={value}
          latest={latest}
          first={first}
          onClose={() => setOpen(false)}
          onPick={(r) => {
            setOpen(false)
            go(r)
          }}
        />
      )}
    </>
  )
}

/** 선택 창. 모바일은 아래에서 올라오는 시트, PC 는 가운데 창. (열릴 때마다 새로 만들어져서 입력칸이 현재 회차로 시작한다) */
function RoundSheet({ value, latest, first, onClose, onPick }) {
  const [text, setText] = useState(String(value))
  const [date, setDate] = useState(roundToDate(value))
  const cur = useRef(null)

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  // 열리면 지금 회차가 목록 가운데에 오도록 스크롤
  useEffect(() => {
    cur.current?.scrollIntoView({ block: 'center' })
  }, [])

  const typed = Number(text)
  const typedOk = Number.isInteger(typed) && typed >= first && typed <= latest
  const dated = dateToRound(date)
  const dateOk = dated != null && dated >= first
  const datedRound = dateOk ? Math.min(dated, latest) : null

  const rounds = Array.from({ length: latest - first + 1 }, (_, i) => latest - i)
  return (
    <div className="sheet" onClick={onClose}>
      <div className="sheet__box rsheet" role="dialog" aria-modal="true" aria-labelledby="round-title"
        onClick={(e) => e.stopPropagation()}>
        <span className="sheet__grip hide-pc" aria-hidden="true" />
        <h2 id="round-title" className="sheet__title">회차 선택</h2>

        <form className="rsheet__row" onSubmit={(e) => { e.preventDefault(); if (typedOk) onPick(typed) }}>
          <label className="rsheet__label" htmlFor="round-num">회차 입력</label>
          <input id="round-num" className="rsheet__input" type="number" inputMode="numeric" min={first} max={latest}
            value={text} onChange={(e) => setText(e.target.value)} />
          <button type="submit" className="btn btn--cta rsheet__go" disabled={!typedOk}>이동</button>
        </form>
        {!typedOk && text !== '' && <p className="rsheet__hint">{first}회부터 {latest}회까지 입력할 수 있어요.</p>}

        <form className="rsheet__row" onSubmit={(e) => { e.preventDefault(); if (datedRound) onPick(datedRound) }}>
          <label className="rsheet__label" htmlFor="round-date">날짜로 찾기</label>
          <input id="round-date" className="rsheet__input" type="date" min={roundToDate(first)} max={roundToDate(latest)}
            value={date} onChange={(e) => setDate(e.target.value)} />
          <button type="submit" className="btn btn--cta rsheet__go" disabled={!datedRound}>
            {datedRound ? `${datedRound}회` : '이동'}
          </button>
        </form>
        <p className="rsheet__hint">고른 날짜까지 가장 가까운 추첨 회차로 이동해요. (추첨은 매주 토요일)</p>

        <h3 className="rsheet__sub">목록에서 고르기</h3>
        <ul className="rsheet__list">
          {rounds.map((r) => (
            <li key={r}>
              <button type="button" ref={r === value ? cur : undefined}
                className={`rsheet__item${r === value ? ' is-on' : ''}`} aria-current={r === value ? 'true' : undefined}
                onClick={() => onPick(r)}>
                <b>{r}회</b>
                <span>{roundToDate(r)}{r === latest ? ' · 최신' : ''}</span>
              </button>
            </li>
          ))}
        </ul>
        <button type="button" className="limit__close" onClick={onClose}>닫기</button>
      </div>
    </div>
  )
}
