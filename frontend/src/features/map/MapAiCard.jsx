import { useRef, useState } from 'react'
import Icon from '../../components/ui/Icon.jsx'
import useApi from '../../hooks/useApi.js'
import LimitSheet from '../ai/LimitSheet.jsx'
import { HISTORY_LIMIT, MAX_QUESTION, fetchAiUsage } from '../analyst/analystApi.js'
import { askMapAi } from './mapApi.js'

const FIRST = '가까운 명당이나 지역 조건으로 물어보세요. 처음 쓰신다면 아래 [지도 AI 사용법]을 먼저 읽고 오시면 좋아요.'

/**
 * 지도 AI 카드(UI-11). 판매점·명당·당첨 이력 질문에 답한다. (API-07, context MAP)
 *  - 가장 최근 답변 하나만 보여 준다. 대화는 서버에 저장되지 않는다. (이어 묻기용 이전 대화는 화면이 들고 있다가 보낸다)
 *  - 위치 스위치가 켜져 있으면 위치를 함께 보낸다. 꺼져 있을 때 "내 주변"을 물으면 서버가 안내 문구로 답한다.
 *  - 무료 횟수는 AI 애널리스트·꿈풀이와 함께 쓴다. 다 쓰면 안내 창(LimitSheet)을 연다.
 */
export default function MapAiCard({ value, onChange, me, onOpenGuide }) {
  const usage = useApi(fetchAiUsage, [])
  const [busy, setBusy] = useState(false)
  const [reply, setReply] = useState({ text: FIRST, charged: false, error: false })
  const [remaining, setRemaining] = useState(null)
  const [limitOpen, setLimitOpen] = useState(false)
  const turns = useRef([])

  const u = usage.data
  const left = remaining !== null ? remaining : u && !u.unlimited ? u.remainingToday : null
  const badge = u?.unlimited ? '제한 없음' : left !== null && u ? `${left}/${u.dailyFreeLimit}` : null

  async function onSubmit(e) {
    e.preventDefault()
    const q = value.trim()
    if (!q || busy) return
    setBusy(true)
    try {
      const r = await askMapAi(q, turns.current.slice(-HISTORY_LIMIT), me)
      turns.current = [
        ...turns.current,
        { role: 'user', content: q },
        { role: 'assistant', content: r.historyText || r.answer || r.message || '' },
      ]
      if (r.remainingToday !== null && r.remainingToday !== undefined) setRemaining(r.remainingToday)
      setReply({ text: r.answer || r.message || '', charged: Boolean(r.charged), error: false })
      onChange('')
    } catch (err) {
      if (err?.errorCode === 'AI-004') {
        setRemaining(0)
        setLimitOpen(true)
      }
      setReply({ text: err?.message || '답변을 만들지 못했어요. 잠시 후 다시 시도해 주세요.', charged: false, error: true })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card card--hero mapai" aria-label="지도 AI">
      <div className="smap__head">
        <h2 className="smap__title">지도 AI</h2>
        {badge && <span className="chip chip--sm is-on" title="오늘 남은 무료 횟수">오늘 남은 횟수 {badge}</span>}
      </div>
      <form className="mapai__form" onSubmit={onSubmit}>
        <input className="mapai__input" type="text" value={value} maxLength={MAX_QUESTION}
          placeholder="AI에게 물어보세요 (예: 가까운 명당)" aria-label="지도 AI에게 질문"
          autoComplete="off" onChange={(e) => onChange(e.target.value)} />
        <button type="submit" className="mapai__send" disabled={busy || !value.trim()} aria-label="보내기">
          {busy ? <span className="spinner spinner--sm" aria-hidden="true" /> : <Icon name="send" size={22} />}
        </button>
      </form>
      <div className="mapai__row">
        <span className="mapai__ava" aria-hidden="true"><Icon name="sparkle" size={18} /></span>
        <p className={`mapai__bubble${reply.error ? ' is-error' : ''}`} role="status" aria-live="polite">
          {busy ? '찾고 있어요…' : reply.text}
        </p>
      </div>
      {!busy && reply.charged && <p className="mapai__charge">무료 횟수 1회를 사용했어요.</p>}
      <button type="button" className="mapai__link" onClick={onOpenGuide}>지도 AI 사용법 ›</button>
      <LimitSheet open={limitOpen} onClose={() => setLimitOpen(false)} />
    </section>
  )
}
