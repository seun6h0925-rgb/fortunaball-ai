import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../../components/ui/Icon.jsx'
import LottoBall from '../../components/ui/LottoBall.jsx'
import { CHIP_LABELS, originLabel, regionLabel } from './dreamApi.js'

/** 게임 이름: A, B, C ... (게임이 둘 이상일 때만 붙인다) */
const gameName = (i) => (i < 26 ? String.fromCharCode(65 + i) : String(i + 1))

/**
 * 꿈풀이 대화 목록. 메시지 종류(kind):
 *  - user   사용자 말풍선
 *  - ai     AI 답변 : text(말), interpretation(풀이 글), symbols(상징 카드), numbers/games(번호와 출처),
 *                     question(되묻는 말), foot(참고 문구), chips(눌러서 답하는 칩), analyzeLink(분석 보기 주소)
 *  - issued 발급 확정 안내
 *  - error  오류 안내
 * 칩은 "맨 마지막 AI 말풍선"에서만 눌리게 한다. (지난 질문의 칩이 남아 있으면 헷갈리기 때문)
 * 새 메시지가 생기면 맨 아래로 스크롤한다.
 */
export default function DreamMessages({ messages, busy, onChip, onOpenGuide }) {
  const endRef = useRef(null)
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end', behavior: 'smooth' })
  }, [messages.length, busy])

  const lastAiId = [...messages].reverse().find((m) => m.kind !== 'user')?.id

  return (
    <div className="chat__list" role="log" aria-live="polite" aria-label="대화">
      {messages.map((m) =>
        m.kind === 'user' ? (
          <div key={m.id} className="msg msg--user">
            <p className="bubble bubble--user">{m.text}</p>
          </div>
        ) : (
          <div key={m.id} className="msg msg--ai">
            <span className="avatar" aria-hidden="true">
              <Icon name="sparkle" size={18} />
            </span>
            <div className="msg__body">
              <AiBubble m={m} active={m.id === lastAiId && !busy} onChip={onChip} onOpenGuide={onOpenGuide} />
            </div>
          </div>
        ),
      )}
      {busy && (
        <div className="msg msg--ai" aria-label="꿈을 풀이하는 중이에요">
          <span className="avatar" aria-hidden="true">
            <Icon name="sparkle" size={18} />
          </span>
          <div className="msg__body">
            <p className="bubble bubble--ai bubble--typing">
              <span className="dots" aria-hidden="true">
                <i />
                <i />
                <i />
              </span>
              꿈을 풀이하는 중이에요
            </p>
          </div>
        </div>
      )}
      <div ref={endRef} />
    </div>
  )
}

function AiBubble({ m, active, onChip, onOpenGuide }) {
  return (
    <>
      <div className={`bubble bubble--ai${m.kind === 'error' ? ' is-error' : ''}`}>
        {m.tag && <span className="dtag">{m.tag}</span>}
        {m.text && <p className="bubble__text">{m.text}</p>}
        {m.interpretation && <p className="bubble__text dream__interp">{m.interpretation}</p>}

        {m.symbols?.length > 0 && (
          <div className="dsym" aria-label="꿈에서 찾은 상징">
            {m.symbols.map((s) => (
              <SymbolCard key={s.keyword} s={s} />
            ))}
          </div>
        )}

        {m.games?.length > 1 ? (
          <div className="dnums" aria-label="풀이 번호">
            {m.games.map((g, i) => (
              <div key={i} className="dnums__game">
                <span className="dnums__name">{gameName(i)}</span>
                <NumberRow list={g} />
              </div>
            ))}
          </div>
        ) : (
          m.numbers?.length > 0 && (
            <div className="dnums" aria-label="풀이 번호">
              <span className="dnums__title">풀이 번호 {m.numbers.length}개</span>
              <NumberRow list={m.numbers} />
            </div>
          )
        )}

        {m.question && <p className="bubble__text dream__q">{m.question}</p>}
        {m.foot && <p className="bubble__foot">{m.foot}</p>}
        {m.charged && <p className="bubble__used">무료 횟수 1회 사용</p>}
      </div>

      {active && (m.chips?.length > 0 || m.analyzeLink || m.action === 'OPEN_GUIDE' || m.suggestions?.length > 0) && (
        <div className="msg__chips">
          {m.chips?.map((c) => (
            <button key={c} type="button" className={`chip chip--sm${c === m.primaryChip ? ' is-on' : ''}`} onClick={() => onChip(c)}>
              {CHIP_LABELS[c] || c}
            </button>
          ))}
          {m.analyzeLink && (
            <Link to={m.analyzeLink} className="chip chip--sm">
              분석 보기
            </Link>
          )}
          {m.suggestions?.map((s) => (
            <button key={s} type="button" className="chip chip--sm" onClick={() => onChip(s, true)}>
              {s}
            </button>
          ))}
          {m.action === 'OPEN_GUIDE' && (
            <button type="button" className="chip chip--sm is-on" onClick={onOpenGuide}>
              사용 방법 보기
            </button>
          )}
        </div>
      )}

      {m.issueLink && (
        <div className="msg__chips">
          <Link to="/generate/issued" className="chip chip--sm is-on">
            발급 결과 보기
          </Link>
        </div>
      )}
    </>
  )
}

// 상징 하나: 이름 + 해석 줄들(한국 전통/해외 전통 표시)
function SymbolCard({ s }) {
  return (
    <div className="dsym__card">
      <b className="dsym__name">{s.keyword}</b>
      {s.interpretations.slice(0, 2).map((it, i) => (
        <div key={i} className="dsym__row">
          <span className={`dtag dtag--${it.region === 'KR' ? 'kr' : 'gl'}`}>{regionLabel(it.region)}</span>
          <span className="dsym__text">{it.summary}</span>
        </div>
      ))}
    </div>
  )
}

// 번호 줄: 로또볼 + 아래에 출처 이름
function NumberRow({ list }) {
  return (
    <div className="dnums__row">
      {list.map((n) => (
        <div key={n.number} className="dnum">
          <LottoBall n={n.number} />
          <span className={`dnum__origin${n.origin === 'STAT' ? ' is-stat' : ''}`}>{originLabel(n.origin)}</span>
        </div>
      ))}
    </div>
  )
}
