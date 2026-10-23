import { useRef, useState } from 'react'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { fetchAiUsage } from '../features/analyst/analystApi.js'
import GuideContent from '../features/analyst/GuideContent.jsx'
import GuideDialog from '../features/analyst/GuideDialog.jsx'
import LimitSheet from '../features/ai/LimitSheet.jsx'
import {
  AWAITING_STEPS,
  CHIP_SPOKEN,
  DREAM_EXAMPLES,
  DREAM_GUIDE_DETAILS,
  DREAM_GUIDE_ITEMS,
  DREAM_GUIDE_LEAD,
  DREAM_GUIDE_NOTE,
  DREAM_MAX,
  NEW_STORY_LENGTH,
  askDream,
} from '../features/dream/dreamApi.js'
import DreamMessages from '../features/dream/DreamMessages.jsx'
import { confirmIssue } from '../features/generate/generateApi.js'
import { saveIssued } from '../features/generate/generateStore.js'
import MakeTabs from '../features/generate/MakeTabs.jsx'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

// 서버에 다시 보내는 최근 대화 개수 (서버도 6개까지만 쓴다)
const TALK_LIMIT = 6

const GREETING = {
  id: 0,
  kind: 'ai',
  text: '안녕하세요. 꿈 이야기를 들려주세요. 꿈 속 상징을 풀이해 드려요. 꿈풀이는 재미로 참고해 주세요. 처음 쓰신다면 아래 [사용 방법 보기]를 먼저 읽고 오시면 좋아요.',
  action: 'OPEN_GUIDE',
  foot: `꿈 이야기는 ${DREAM_MAX}자 이내로 적어 주세요. 꿈에 숫자(1~45)가 나오면 번호 후보로 써요.`,
}

/**
 * AI 로또 꿈풀이(UI-06, 06B, 06C).
 * 꿈 이야기를 서버(API-08)에 보내면 상징 풀이와 번호가 오고, 서버가 되묻는 질문에 답하며 이어지는 대화 화면이다.
 *  - 서버는 꿈 내용을 저장하지 않는다. 그래서 이 화면(ctxRef)이 "처음 꿈 + 직전 응답의 step·keep·gameCount·confirmToken"을 들고 있다가
 *    이어지는 요청에 그대로 다시 보낸다.
 *  - 서버가 답을 기다리는 단계(AWAITING_STEPS)에서는, 짧게 쓴 글은 "답"으로, 긴 글(NEW_STORY_LENGTH 이상)은 "새 꿈 이야기"로 보낸다.
 *  - 눌러서 답하는 칩(quickReplies)은 그 문구를 reply 로 그대로 보낸다. 말풍선에는 사용자가 말한 것처럼 보여 준다.
 *  - 서버가 action=ISSUE 를 주면(사용자가 확정함) 확정 토큰으로 발급 확정(API-04)을 한다.
 *  - 무료 횟수가 다 되면(오류 AI-004) 안내 창(LimitSheet)을 연다.
 * 📚 배운 내용: useRef 로 요청 사이에 값 유지, async/await, 상태 배열에 메시지 쌓기 (AI 애널리스트 화면과 같은 방식)
 */
export default function DreamPage() {
  usePageTitle('AI 로또 꿈풀이')
  const usage = useApi(fetchAiUsage, [])

  const [messages, setMessages] = useState([GREETING])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [guideOpen, setGuideOpen] = useState(false)
  const [limitOpen, setLimitOpen] = useState(false)
  const [remaining, setRemaining] = useState(null)

  const idRef = useRef(1)
  // 서버와 이어 가기 위한 값: { dream, step, keep, gameCount, confirmToken }. 새 꿈을 풀면 바뀐다.
  const ctxRef = useRef(null)
  // 꿈에 대해 주고받은 최근 대화 [{ role, content }]. 서버가 기억하지 않으므로 요청마다 다시 보낸다. 새 꿈을 풀면 비운다.
  const talkRef = useRef([])
  const inputRef = useRef(null)

  const push = (m) => {
    const id = idRef.current++
    setMessages((list) => [...list, { id, ...m }])
  }

  const u = usage.data
  const left = remaining !== null ? remaining : u && !u.unlimited ? u.remainingToday : null
  const badge = u?.unlimited ? '제한 없음' : left !== null && u ? `${left}/${u.dailyFreeLimit}` : null

  function fillInput(text) {
    setInput(text)
    setGuideOpen(false)
    requestAnimationFrame(() => {
      const el = inputRef.current
      if (!el) return
      el.focus()
      el.setSelectionRange(text.length, text.length)
    })
  }

  /**
   * 서버에 보낸다.
   *  raw     : 사용자가 한 말 (칩이면 칩 문구)
   *  shown   : 말풍선에 보여 줄 말 (안 주면 raw)
   *  asReply : true 면 무조건 "직전 질문에 대한 답"으로 보낸다 (칩)
   */
  async function send(raw, shown, asReply = false) {
    const text = raw.trim()
    if (!text || busy) return
    if (text.length > DREAM_MAX) {
      push({ kind: 'error', text: `꿈 이야기는 ${DREAM_MAX}자 이내로 적어 주세요.` })
      return
    }

    const ctx = ctxRef.current
    const awaiting = ctx && AWAITING_STEPS.includes(ctx.step)
    // 답으로 보낼지(reply), 새 꿈 이야기로 보낼지(dream) 정한다
    const isReply = Boolean(ctx) && (asReply || (awaiting && text.length < NEW_STORY_LENGTH))
    push({ kind: 'user', text: shown || text })
    setInput('')
    setBusy(true)
    const history = talkRef.current.slice(-TALK_LIMIT)
    // 새 이야기처럼 보내는 말에도 지금 이야기 중인 꿈(ongoing)을 같이 보낸다. 그래야 "그 꿈에 맞는 번호가 있어?" 같은 질문에 이어서 답한다.
    const body = isReply
      ? { dream: ctx.dream, reply: text, step: ctx.step, keep: ctx.keep, gameCount: ctx.gameCount, confirmToken: ctx.confirmToken, history }
      : ctx
        ? { dream: text, ongoing: ctx.dream, step: ctx.step, history }
        : { dream: text }
    try {
      const r = await askDream(body)
      // 인사·너무 짧음 같은 말만 돌려주는 응답은, 진행 중이던 대화(확정 대기 등)를 끊지 않는다. 대화 기록에는 남겨서 다음 말에 이어지게 한다.
      if (r.step === 'TALK' || r.step === 'NEED_MORE') {
        talkRef.current = [
          ...talkRef.current,
          { role: 'user', content: text },
          { role: 'assistant', content: r.message || '' },
        ].slice(-TALK_LIMIT)
      } else {
        if (!isReply) talkRef.current = [] // 새 꿈을 풀기 시작하면 이전 대화는 비운다
        ctxRef.current = {
          dream: isReply ? ctx.dream : text,
          step: r.step,
          keep: r.keep,
          gameCount: r.gameCount,
          confirmToken: r.confirmToken,
        }
      }
      if (r.remainingToday !== null && r.remainingToday !== undefined) setRemaining(r.remainingToday)
      pushAnswer(r)
      if (r.action === 'ISSUE' && r.confirmToken) await issue(r.confirmToken)
    } catch (err) {
      if (err?.errorCode === 'AI-004') {
        setRemaining(0)
        setLimitOpen(true)
      }
      push({ kind: 'error', text: err?.message || '꿈을 풀이하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    } finally {
      setBusy(false)
    }
  }

  // 서버 응답을 말풍선 하나로 만든다
  function pushAnswer(r) {
    const nums = (r.numbers || []).map((n) => n.number)
    const single = !(r.games && r.games.length > 1)
    const done = r.step === 'CONFIRM' || r.step === 'CONFIRMED'
    // 해석 기준·참고 안내는 풀이 결과(상징·번호)가 있을 때만 붙인다. 인사·잡담 답변에는 붙이지 않는다.
    const hasResult = (r.symbols?.length || 0) > 0 || (r.numbers?.length || 0) > 0
    const foot = hasResult ? [r.basisNotice, r.notice].filter(Boolean).join(' ') : ''
    push({
      kind: 'ai',
      tag: r.policyCode ? '안내' : null,
      text: r.message || '',
      interpretation: r.interpretation,
      symbols: r.symbols,
      numbers: r.numbers,
      games: r.games,
      question: r.question,
      foot: foot || null,
      chips: r.quickReplies || [],
      primaryChip: r.quickReplies?.[0],
      // 눌러서 답하는 칩이 있으면 추천 문구는 보여 주지 않는다 (선택지가 섞여 헷갈리기 때문)
      suggestions: r.quickReplies?.length ? [] : r.suggestions,
      action: r.action,
      charged: Boolean(r.charged),
      // 번호가 한 게임(6개)으로 완성되면 AI 애널리스트로 넘겨 분석해 볼 수 있다
      analyzeLink: done && single && nums.length === 6 ? `/analyst?numbers=${[...nums].sort((a, b) => a - b).join(',')}` : null,
    })
  }

  // 발급 확정(API-04). 서버가 확정 토큰과 함께 action=ISSUE 를 줬을 때만 부른다.
  async function issue(token) {
    try {
      const issued = await confirmIssue(token)
      saveIssued(issued)
      ctxRef.current = null
      talkRef.current = []
      push({
        kind: 'ai',
        text: `발급을 확정했어요. 발급 코드는 ${issued.issueCode} 예요. 번호는 내 발급 내역에서도 볼 수 있어요.`,
        issueLink: true,
      })
    } catch (err) {
      push({ kind: 'error', text: err?.message || '발급을 확정하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    }
  }

  // 칩 누르기: 질문에 대한 답이면 reply 로, 추천 질문(suggest)이면 새 이야기로 보낸다
  function onChip(label, isSuggestion = false) {
    send(label, CHIP_SPOKEN[label] || label, !isSuggestion)
  }

  function onSubmit(e) {
    e.preventDefault()
    send(input)
  }

  return (
    <div className="ana dream">
      <PageHeader title="AI 로또 꿈풀이" tagline>
        <p className="phead__sub only-pc">꿈 속 상징을 풀이하고 참고 번호를 받아 보세요</p>
        <p className="phead__sub hide-pc">꿈 상징 풀이 · 참고 번호</p>
        <MakeTabs />
      </PageHeader>

      <div className="ana__grid">
        <aside className="card card--hero ana__guide only-pc" aria-label="이렇게 물어보세요">
          <h2 className="ana__guide-title">이렇게 물어보세요</h2>
          <p className="sub">누르면 입력창에 바로 들어가요</p>
          <GuideContent items={DREAM_GUIDE_ITEMS} showDesc={false} onPick={(t) => fillInput(t)} />
          <button type="button" className="btn btn--ghost btn--block" onClick={() => setGuideOpen(true)}>
            사용법 전체 보기
          </button>
          <p className="ana__guide-note">
            번호는 상징 목록, 꿈에 적은 숫자, 통계에서만 가져오고 AI가 임의로 만들지 않아요.
            <br />
            꿈 내용은 저장되지 않고, 답변은 500자 이내로 제공돼요.
          </p>
        </aside>

        <section className="card card--hero chat" aria-label="AI 로또 꿈풀이 대화">
          <header className="chat__head">
            <span className="avatar avatar--lg" aria-hidden="true">
              <Icon name="moon" size={22} />
            </span>
            <div className="chat__titles">
              <h2 className="chat__title">AI 로또 꿈풀이</h2>
              <p className="chat__sub">꿈 상징 풀이 · 참고 번호</p>
            </div>
            <div className="chat__tools">
              {badge && (
                <span className="chip chip--sm is-on chat__left" title="오늘 남은 무료 횟수">
                  <span className="only-pc">오늘 남은 횟수 </span>
                  <span className="hide-pc">오늘 </span>
                  {badge}
                </span>
              )}
              <button type="button" className="chip chip--sm" onClick={() => setGuideOpen(true)}>
                사용법
              </button>
            </div>
          </header>

          <DreamMessages messages={messages} busy={busy} onChip={onChip} onOpenGuide={() => setGuideOpen(true)} />

          <div className="chat__foot">
            <div className="chat__quick" aria-label="꿈 예시">
              {DREAM_EXAMPLES.map((ex) => (
                <button key={ex} type="button" className="chip chip--sm" onClick={() => fillInput(ex)} disabled={busy}>
                  {ex}
                </button>
              ))}
            </div>
            <form className="chat__form" onSubmit={onSubmit}>
              <input
                ref={inputRef}
                className="chat__input"
                type="text"
                value={input}
                maxLength={DREAM_MAX}
                onChange={(e) => setInput(e.target.value)}
                placeholder={`꿈 이야기를 들려주세요 (${DREAM_MAX}자 이내)`}
                aria-label="꿈 이야기 입력"
                autoComplete="off"
              />
              <button type="submit" className="chat__send" disabled={busy || !input.trim()} aria-label="보내기">
                <Icon name="send" size={22} />
              </button>
            </form>
            <div className="dream__meta">
              <span>AI가 답할 때마다 무료 횟수 1회가 차감돼요. 꿈과 벗어난 대화나 다시 생성도 차감돼요. 인사·사용법·확정은 차감되지 않아요.</span>
              <span className="dream__count" aria-label="글자 수">
                {input.length}/{DREAM_MAX}
              </span>
            </div>
            <p className="chat__note">
              통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다. 꿈 내용은 저장되지 않고, 답변은 500자 이내로 제공돼요.
            </p>
          </div>
        </section>
      </div>

      <AdSlot />
      <NoticeBox />
      <GuideDialog
        open={guideOpen}
        onClose={() => setGuideOpen(false)}
        onPick={(t) => fillInput(t)}
        items={DREAM_GUIDE_ITEMS}
        details={DREAM_GUIDE_DETAILS}
        lead={DREAM_GUIDE_LEAD}
        note={DREAM_GUIDE_NOTE}
      />
      <LimitSheet open={limitOpen} onClose={() => setLimitOpen(false)} />
    </div>
  )
}
