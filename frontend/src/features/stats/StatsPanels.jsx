import { useState } from 'react'
import { Link } from 'react-router-dom'
import LottoBall from '../../components/ui/LottoBall.jsx'
import { Donut, HBars, NumberBars, SumLine, VBars } from './charts.jsx'
import { PERIODS, TABS, periodText } from './statsApi.js'

// 통계 분석(UI-07)의 탭별 내용. 모든 숫자는 서버(API-09·10·44)가 준 값 그대로이고, 화면에서 새로 계산하지 않는다.
// (막대 길이·비율 표시처럼 모양을 위한 계산만 한다)

/** 카드 하나. title 오른쪽에 action(더보기 등)을 둘 수 있다. */
function Panel({ title, action, children, className = '' }) {
  return (
    <section className={`card card--hero spanel ${className}`}>
      <div className="spanel__head">
        <h2 className="spanel__title">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

function PeriodChips({ value, onChange }) {
  return (
    <div className="spanel__periods" role="group" aria-label="기간">
      {PERIODS.map((p) => (
        <button key={p.value} type="button" className={`chip chip--sm${p.value === value ? ' is-on' : ''}`}
          aria-pressed={p.value === value} onClick={() => onChange(p.value)}>
          {p.label}
        </button>
      ))}
    </div>
  )
}

/** "더보기 ›" 토글. 더 보여 줄 줄이 없으면 아무것도 그리지 않는다. */
function MoreButton({ total, shown, open, onToggle }) {
  if (total <= shown) return null
  return (
    <button type="button" className="spanel__more" onClick={onToggle} aria-expanded={open}>
      {open ? '접기 ‹' : '더보기 ›'}
    </button>
  )
}

/** 한쪽이 더 많았는지 (▲ 홀 많음 / – 균형 / ▼ 짝 많음) */
function Flow({ a, b, aName, bName }) {
  if (a === b) return <span className="flow flow--even">– 균형</span>
  return a > b ? <span className="flow up">▲ {aName} 많음</span> : <span className="flow down">▼ {bName} 많음</span>
}

const changeText = (c) => (c == null ? null : c > 0 ? { cls: 'up', t: `▲ ${c}` } : c < 0 ? { cls: 'down', t: `▼ ${Math.abs(c)}` } : { cls: '', t: '–' })

// ── 핫넘버 ──────────────────────────────────────────────
export function HotPanel({ s, nums, period, setPeriod }) {
  const [more, setMore] = useState(false)
  const hot = s.hotNumbers
  const rows = more ? hot : hot.slice(0, 5)
  return (
    <>
      <Panel title="번호별 출현 횟수">
        <PeriodChips value={period} onChange={setPeriod} />
        {nums ? <NumberBars items={nums.items} hot={hot.slice(0, 5).map((h) => h.number)} /> : <div className="skeleton" style={{ height: 200 }} />}
        <p className="spanel__note">밝은 막대는 {periodText(period)}에서 가장 많이 나온 5개 번호예요.</p>
      </Panel>
      <Panel title={`많이 나온 번호 TOP ${rows.length}`} action={<MoreButton total={hot.length} shown={5} open={more} onToggle={() => setMore((v) => !v)} />}>
        <ol className="rank">
          {rows.map((h, i) => {
            const c = changeText(h.change)
            return (
              <li key={h.number} className="rank__row">
                <span className="rank__no">{i + 1}</span>
                <LottoBall n={h.number} />
                <span className="rank__name">{h.number}번</span>
                <span className="rank__val">{h.count}회</span>
                <span className={`rank__chg ${c?.cls ?? ''}`}>{c?.t ?? ''}</span>
              </li>
            )
          })}
        </ol>
        {hot.some((h) => h.change != null) && <p className="spanel__note">오른쪽 화살표는 바로 앞 같은 길이 구간보다 출현 횟수가 늘거나 줄어든 수예요.</p>}
      </Panel>
    </>
  )
}

// ── 겨울잠 번호 ──────────────────────────────────────────
export function WinterPanel({ s }) {
  const [more, setMore] = useState(false)
  const a = s.absence
  const list = a.numbers
  const longRows = list.slice(0, 8)
  const lastRows = more ? list : list.slice(0, 3)
  const max = Math.max(1, ...list.map((n) => n.absentRounds))
  return (
    <>
      <Panel title="오래 나오지 않은 번호" action={<span className="spanel__basis">기준 {s.basisRound}회 ›</span>}>
        <div className="tiles">
          <div className="tile"><span className="sub">최장 미출현</span><b>{a.maxAbsent}회</b></div>
          <div className="tile"><span className="sub">전체 평균 간격</span><b>{a.averageInterval}회</b></div>
          <div className="tile"><span className="sub">10회 이상</span><b>{a.over10Count}개</b></div>
        </div>
        <ol className="rank">
          {longRows.map((n, i) => (
            <li key={n.number} className="rank__row rank__row--bar">
              <span className="rank__no">{i + 1}</span>
              <LottoBall n={n.number} />
              <div className="rank__mid">
                <div className="rank__line">
                  <span className="rank__name">{n.number}번</span>
                  <span className="up rank__val">{n.absentRounds}회째 미출현</span>
                </div>
                <span className="hbars__track"><span className="hbars__fill" style={{ width: `${(n.absentRounds / max) * 100}%` }} /></span>
              </div>
            </li>
          ))}
        </ol>
      </Panel>
      <Panel title="번호별 마지막 출현 회차" action={<MoreButton total={list.length} shown={3} open={more} onToggle={() => setMore((v) => !v)} />}>
        <ul className="last">
          {lastRows.map((n) => (
            <li key={n.number} className="last__row">
              <b>{n.number}번</b>
              <span className="sub">{n.lastRound ? `${n.lastRound}회` : '기록 없음'}</span>
              <span>{n.absentRounds}회 전</span>
            </li>
          ))}
        </ul>
      </Panel>
    </>
  )
}

// ── 밸런스 믹스 / 하이로우 믹스 (같은 모양) ────────────────
const SPLIT = {
  balance: { key: 'oddEven', a: '홀수', b: '짝수', aShort: '홀', bShort: '짝', ca: 'odd', cb: 'even', title: '홀수:짝수', note: (p) => `${periodText(p)} 당첨번호 6개(보너스 제외)의 홀짝 비율이에요.` },
  highlow: { key: 'highLow', a: '저(1~22)', b: '고(23~45)', aShort: '저', bShort: '고', ca: 'low', cb: 'high', title: '저(1~22):고(23~45)', note: (p) => `1~22를 저, 23~45를 고로 나눈 ${periodText(p)} 비율이에요.` },
}

export function SplitPanel({ s, kind, period, setPeriod }) {
  const [more, setMore] = useState(false)
  const cfg = SPLIT[kind]
  const sp = s[cfg.key]
  const combos = Object.entries(sp.combos).map(([label, count]) => ({ label, count }))
  const rounds = s.recentRounds
  const shown = more ? rounds : rounds.slice(0, 5)
  return (
    <>
      <Panel title="전체 비율" action={null}>
        <PeriodChips value={period} onChange={setPeriod} />
        <div className="split">
          <Donut firstPercent={sp.firstPercent} centerTop={`${Math.round(sp.firstPercent)}%`} centerBottom={cfg.a} />
          <div className="split__legend">
            <div className="split__row"><i className="dot dot--a" />{cfg.a}<b>{Math.round(sp.firstPercent)}%</b></div>
            <div className="split__row"><i className="dot dot--b" />{cfg.b}<b>{Math.round(sp.secondPercent)}%</b></div>
            <p className="spanel__note">{cfg.note(period)}</p>
          </div>
        </div>
      </Panel>
      <Panel title="비율 조합별 출현 횟수">
        <p className="spanel__note">{cfg.title} 개수 기준, {periodText(period)}</p>
        <HBars rows={combos} />
      </Panel>
      <Panel title="최근 5회 흐름" action={<MoreButton total={rounds.length} shown={5} open={more} onToggle={() => setMore((v) => !v)} />}>
        <ul className="last">
          {shown.map((r) => {
            const x = kind === 'balance' ? [r.odd, r.even] : [r.low, r.high]
            return (
              <li key={r.roundNo} className="last__row">
                <span className="sub">{r.roundNo}회</span>
                <b>{cfg.aShort} {x[0]} : {cfg.bShort} {x[1]}</b>
                <Flow a={x[0]} b={x[1]} aName={cfg.aShort} bName={cfg.bShort} />
              </li>
            )
          })}
        </ul>
      </Panel>
    </>
  )
}

// ── 센터 존 ──────────────────────────────────────────────
export function CenterPanel({ s, period, setPeriod }) {
  const sum = s.sum
  return (
    <>
      <Panel title="당첨번호 합계 추이">
        <PeriodChips value={period} onChange={setPeriod} />
        <div className="tiles">
          <div className="tile"><span className="sub">평균</span><b>{Math.round(sum.average)}</b></div>
          <div className="tile"><span className="sub">최소</span><b>{sum.min}</b></div>
          <div className="tile"><span className="sub">최대</span><b>{sum.max}</b></div>
        </div>
        <SumLine series={sum.series} />
        <p className="spanel__note">선은 회차별 당첨번호 6개의 합이에요.</p>
      </Panel>
      <Panel title="합계 구간별 출현 횟수">
        <p className="spanel__note">{periodText(period)}</p>
        <HBars rows={sum.bands} />
      </Panel>
    </>
  )
}

// ── 올 구간 스프레드 ─────────────────────────────────────
const SECTION_LABELS = ['1~10', '11~20', '21~30', '31~40', '41~45']

export function SectionPanel({ s, period, setPeriod }) {
  const [more, setMore] = useState(false)
  const rounds = s.recentRounds
  const shown = more ? rounds : rounds.slice(0, 3)
  return (
    <>
      <Panel title="구간별 출현 횟수">
        <PeriodChips value={period} onChange={setPeriod} />
        <VBars rows={s.sections} />
        <p className="spanel__note">밝은 막대는 가장 많이 나온 구간이에요.</p>
      </Panel>
      <Panel title="최근 5회 구간 분포" action={<MoreButton total={rounds.length} shown={3} open={more} onToggle={() => setMore((v) => !v)} />}>
        <ul className="last last--wrap">
          {shown.map((r) => (
            <li key={r.roundNo} className="last__row">
              <span className="sub">{r.roundNo}회</span>
              <span className="last__sections">{r.sectionCounts.map((c, i) => `${SECTION_LABELS[i]} : ${c}`).join(' · ')}</span>
            </li>
          ))}
        </ul>
      </Panel>
    </>
  )
}

// ── "이 통계로 번호 만들기" 카드 (모든 탭 공통) ────────────
// "로/으로" 고르기: 받침이 없거나 ㄹ 받침이면 "로"
function withRo(word) {
  const c = word.charCodeAt(word.length - 1) - 0xac00
  if (c < 0 || c > 11171) return `${word}로`
  const jong = c % 28
  return jong === 0 || jong === 8 ? `${word}로` : `${word}으로`
}

export function MakeCard({ tab, pattern, showHit }) {
  const t = TABS.find((x) => x.key === tab)
  return (
    <section className="card card--hero spanel smake">
      <div className="smake__text">
        <div className="spanel__head">
          <h2 className="spanel__title">{withRo(t.make)} 번호 만들기</h2>
          {pattern && <span className="pcard__badge">{pattern.period}회 기준</span>}
        </div>
        <p className="smake__desc">{pattern?.description ?? '이 통계 조건으로 번호를 구성해요.'}</p>
        {pattern && showHit && <p className="smake__hit">최근 {pattern.period}회 중 {pattern.hitCount}회 해당</p>}
      </div>
      <Link to={`/generate?pattern=${t.code}`} className="btn btn--cta smake__btn">
        이 통계로 번호 생성
      </Link>
    </section>
  )
}
