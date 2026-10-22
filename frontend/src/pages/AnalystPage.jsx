import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { confirmIssue } from '../features/generate/generateApi.js'
import { saveIssued } from '../features/generate/generateStore.js'
import MakeTabs from '../features/generate/MakeTabs.jsx'
import {
  GUIDE_DETAILS,
  GUIDE_ITEMS,
  GUIDE_LEAD,
  GUIDE_NOTE,
  HISTORY_LIMIT,
  MAX_QUESTION,
  askAnalyst,
  fetchAiUsage,
} from '../features/analyst/analystApi.js'
import LimitSheet from '../features/ai/LimitSheet.jsx'
import GuideContent from '../features/analyst/GuideContent.jsx'
import GuideDialog from '../features/analyst/GuideDialog.jsx'
import MessageList from '../features/analyst/MessageList.jsx'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'
import { CODE_LENGTH, normalizeCode } from '../lib/issueCode.js'

const GREETING = {
  id: 0,
  kind: 'ai',
  text: '안녕하세요. 번호를 만들거나 분석하고, 통계도 설명해 드려요. 번호는 통계 분석을 거쳐 만들고, 저는 그 결과를 설명해요. 처음 쓰신다면 아래 [사용 방법 보기]를 먼저 읽고 오시면 좋아요.',
  action: 'OPEN_GUIDE',
}

// "확정할게" 같은 말은 AI 를 부르지 않고(횟수 차감 없이) 화면이 바로 발급 확정으로 처리한다.
const CONFIRM_WORDS = /^\s*(확정|발급)\s*(할게|할래|해줘|해 줘|해요|하자)?\s*[.!]?\s*$/

/**
 * AI 로또 애널리스트(UI-05, 05B).
 * 질문을 서버(API-07)에 보내 답을 받는 대화 화면이다. 대화는 이 화면(메모리)에만 있고, 서버는 저장하지 않는다.
 *  - 번호를 만들어 주면 임시 결과(확정 토큰)가 같이 온다. "확정할게"를 누르거나 말하면 발급 확정(API-04)을 한다.
 *  - "통계 근거 보기"는 응답에 이미 들어 있는 근거를 보여 주는 것이라 AI 를 다시 부르지 않고 횟수도 안 쓴다.
 *  - 발급 내역의 "분석 보기"로 들어오면 주소에 ?code=발급코드 가 있어서, 입력창에 "코드 분석해줘"를 미리 채워 둔다. (보내는 건 사용자)
 * 📚 배운 내용: useRef(다시 그려도 값이 유지되는 보관함), 비동기(async/await) 처리, 상태 배열에 메시지 쌓기
 */
export default function AnalystPage() {
  usePageTitle('AI 로또 애널리스트')
  const [params] = useSearchParams()
  const usage = useApi(fetchAiUsage, [])

  const [messages, setMessages] = useState([GREETING])
  const [input, setInput] = useState(() => {
    const code = normalizeCode(params.get('code') || '')
    if (code.length === CODE_LENGTH) return `${code} 분석해줘`
    // 꿈풀이에서 넘어온 번호 (?numbers=3,4,8,12,26,32)
    const nums = (params.get('numbers') || '').split(',').map((n) => n.trim()).filter((n) => /^\d{1,2}$/.test(n))
    return nums.length === 6 ? `${nums.join(', ')} 분석해줘` : ''
  })
  const [busy, setBusy] = useState(false)
  const [guideOpen, setGuideOpen] = useState(false)
  const [limitOpen, setLimitOpen] = useState(false)
  const [remaining, setRemaining] = useState(null) // 응답이 알려 준 최신 남은 횟수

  const idRef = useRef(1)
  const turnsRef = useRef([]) // 서버에 다시 보낼 이전 대화 [{ role, content }]
  const lastGenQuestionRef = useRef('') // "다시 뽑아줘"용: 마지막으로 번호를 만든 질문
  const inputRef = useRef(null)

  const nextId = () => idRef.current++
  const push = (m) => {
    const id = nextId()
    setMessages((list) => [...list, { id, ...m }])
  }

  // 남은 횟수 표시: 응답에서 받은 값이 있으면 그것, 없으면 처음 조회한 값
  const u = usage.data
  const left = remaining !== null ? remaining : u && !u.unlimited ? u.remainingToday : null
  const badge = u?.unlimited ? '제한 없음' : left !== null && u ? `${left}/${u.dailyFreeLimit}` : null

  function fillInput(text, caretAtStart = false) {
    setInput(text)
    setGuideOpen(false)
    requestAnimationFrame(() => {
      const el = inputRef.current
      if (!el) return
      el.focus()
      const pos = caretAtStart ? 0 : text.length
      el.setSelectionRange(pos, pos)
    })
  }

  // 아직 확정하지 않은, 만료되지 않은 가장 최근 임시 번호
  function findPending() {
    for (let i = messages.length - 1; i >= 0; i--) {
      const g = messages[i].generated
      if (g && !messages[i].confirmed && Date.parse(g.expiresAt) > Date.now()) return messages[i]
    }
    return null
  }

  // shown: 대화창 말풍선에 보여 줄 말. 안 주면 질문 그대로. ("다시 생성"은 말풍선에 "다시 생성해줘"로 보이고, 서버에는 원래 질문을 보낸다)
  async function send(raw, shown) {
    const question = raw.trim()
    if (!question || busy) return
    if (question.length > MAX_QUESTION) {
      push({ kind: 'error', text: `질문은 ${MAX_QUESTION}자 이내로 적어 주세요.` })
      return
    }
    if (CONFIRM_WORDS.test(question) && findPending()) {
      setInput('')
      confirmPending()
      return
    }

    push({ kind: 'user', text: shown || question })
    setInput('')
    setBusy(true)
    try {
      const r = await askAnalyst(question, turnsRef.current.slice(-HISTORY_LIMIT))
      turnsRef.current = [
        ...turnsRef.current,
        { role: 'user', content: question },
        { role: 'assistant', content: r.historyText || r.answer || r.message || '' },
      ]
      if (r.generated) lastGenQuestionRef.current = question
      if (r.remainingToday !== null && r.remainingToday !== undefined) setRemaining(r.remainingToday)
      push({
        kind: 'ai',
        text: r.answer || r.message || '',
        games: r.generated?.games,
        generated: r.generated || null,
        evidence: r.evidence && r.evidence.items?.length ? { ...r.evidence, references: r.references } : null,
        canRegenerate: Boolean(r.generated),
        question,
        suggestions: r.suggestions,
        action: r.action,
        charged: Boolean(r.charged),
        footnote: r.generated ? '아직 저장 전이에요. "확정할게"라고 말씀해 주시면 발급이 확정돼요.' : null,
      })
    } catch (err) {
      // 서버가 준 안내 문구를 그대로 보여 준다. (무료 횟수 소진, AI 이용 불가, 요청이 너무 잦음 등)
      if (err?.errorCode === 'AI-004') {
        setRemaining(0)
        setLimitOpen(true)
      }
      push({ kind: 'error', text: err?.message || '답변을 만들지 못했어요. 잠시 후 다시 시도해 주세요.' })
    } finally {
      setBusy(false)
    }
  }

  // "통계 근거 보기": 이미 받아 둔 근거를 보여 준다 (AI 호출·횟수 차감 없음)
  function showEvidence(m) {
    push({ kind: 'user', text: '통계 근거 보기' })
    push({
      kind: 'evidence',
      text: '',
      basis: m.evidence.basis,
      items: m.evidence.items,
      references: m.evidence.references,
    })
  }

  // "확정할게": 임시 번호를 발급 확정(API-04). AI 를 부르지 않는다.
  async function confirmPending() {
    if (busy) return
    const pending = findPending()
    push({ kind: 'user', text: '확정할게' })
    if (!pending) {
      push({ kind: 'ai', text: '확정할 번호가 없어요. 먼저 번호를 만들어 달라고 말씀해 주세요. 예) 7 넣어서 3게임 뽑아줘' })
      return
    }
    setBusy(true)
    try {
      const issued = await confirmIssue(pending.generated.confirmToken)
      saveIssued(issued)
      setMessages((list) => list.map((m) => (m.id === pending.id ? { ...m, confirmed: true, canRegenerate: false } : m)))
      push({
        kind: 'ai',
        text: `발급을 확정했어요. 발급 코드는 ${issued.issueCode} 예요. 번호는 내 발급 내역에서도 볼 수 있어요.`,
        issueLink: true,
      })
    } catch (err) {
      push({ kind: 'error', text: err?.message || '발급을 확정하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    } finally {
      setBusy(false)
    }
  }

  function regenerate(m) {
    send(m.question, '다시 생성해줘')
  }

  function again() {
    if (lastGenQuestionRef.current) send(lastGenQuestionRef.current, '다시 뽑아줘')
    else {
      // 이 대화에서 번호를 만든 적이 없으면 다시 뽑을 게 없다 (새로고침하면 대화가 지워진다)
      push({ kind: 'user', text: '다시 뽑아줘' })
      push({ kind: 'ai', text: '다시 뽑을 번호가 아직 없어요. 먼저 번호를 만들어 달라고 말씀해 주세요. 예) 7 넣어서 3게임 뽑아줘' })
    }
  }

  function onSubmit(e) {
    e.preventDefault()
    send(input)
  }

  return (
    <div className="ana">
      <PageHeader title="AI 로또 애널리스트" tagline>
        <p className="phead__sub only-pc">대화로 번호를 분석하고 만들어 보세요</p>
        <p className="phead__sub hide-pc">번호를 분석하고 · 만들고 · 통계를 물어보세요</p>
        <MakeTabs />
      </PageHeader>

      <div className="ana__grid">
        <aside className="card card--hero ana__guide only-pc" aria-label="질문 예시">
          <h2 className="ana__guide-title">이렇게 물어보세요</h2>
          <p className="sub">누르면 입력창에 바로 들어가요</p>
          <GuideContent items={GUIDE_ITEMS} showDesc={false} onPick={(t) => fillInput(t)} />
          <button type="button" className="btn btn--ghost btn--block" onClick={() => setGuideOpen(true)}>
            사용법 전체 보기
          </button>
          <p className="ana__guide-note">
            번호는 통계 분석을 거쳐 만들고, AI는 그 결과를 설명해요.
            <br />
            대화는 저장되지 않고, 답변은 500자 이내로 제공돼요.
          </p>
        </aside>

        <section className="card card--hero chat" aria-label="AI 로또 애널리스트 대화">
          <header className="chat__head">
            <span className="avatar avatar--lg" aria-hidden="true">
              <Icon name="sparkle" size={22} />
            </span>
            <div className="chat__titles">
              <h2 className="chat__title">AI 로또 애널리스트</h2>
              <p className="chat__sub">번호 분석 · 번호 생성 · 통계 질문</p>
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

          <MessageList
            messages={messages}
            busy={busy}
            onEvidence={showEvidence}
            onRegenerate={regenerate}
            onSuggest={(s) => send(s)}
            onOpenGuide={() => setGuideOpen(true)}
          />

          <div className="chat__foot">
            <div className="chat__quick">
              <button type="button" className="chip chip--sm" onClick={confirmPending} disabled={busy}>
                확정할게
              </button>
              <button type="button" className="chip chip--sm" onClick={again} disabled={busy}>
                다시 뽑아줘
              </button>
              <button type="button" className="chip chip--sm" onClick={() => fillInput(' 분석해줘', true)} disabled={busy}>
                내 번호 분석해줘
              </button>
            </div>
            <form className="chat__form" onSubmit={onSubmit}>
              <input
                ref={inputRef}
                className="chat__input"
                type="text"
                value={input}
                maxLength={MAX_QUESTION}
                onChange={(e) => setInput(e.target.value)}
                placeholder="메시지를 입력하세요"
                aria-label="메시지 입력"
                autoComplete="off"
              />
              <button type="submit" className="chat__send" disabled={busy || !input.trim()} aria-label="보내기">
                <Icon name="send" size={22} />
              </button>
            </form>
            <p className="chat__note">
              통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다. AI가 답하면 무료 횟수 1회가 차감돼요. 주제에서 벗어난 대화도 AI가 답하면 차감돼요. 대화는 저장되지
              않아요.
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
        items={GUIDE_ITEMS}
        details={GUIDE_DETAILS}
        lead={GUIDE_LEAD}
        note={GUIDE_NOTE}
      />
      <LimitSheet open={limitOpen} onClose={() => setLimitOpen(false)} />
    </div>
  )
}
