import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../../components/ui/Icon.jsx'
import LottoBall from '../../components/ui/LottoBall.jsx'

/** 게임 이름 한 글자: A, B, C ... Z, 그 뒤로는 숫자 (대화 안에서는 짧게 보여 준다) */
const shortLabel = (i) => (i < 26 ? String.fromCharCode(65 + i) : String(i + 1))

/**
 * 대화 목록. 메시지 종류(kind):
 *  - user     사용자 말풍선
 *  - ai       AI 답변 (번호를 만들었으면 games, 추천 질문이면 suggestions, 근거가 있으면 evidence 버튼)
 *  - evidence "통계 근거 보기" 눌렀을 때 보여 주는 근거 목록
 *  - issued   확정(발급) 완료 안내
 *  - error    오류 안내
 * 새 메시지가 생기면 맨 아래로 스크롤한다.
 */
export default function MessageList({ messages, busy, onEvidence, onRegenerate, onSuggest, onOpenGuide }) {
  const endRef = useRef(null)
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end', behavior: 'smooth' })
  }, [messages.length, busy])

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
              <AiBubble m={m} onEvidence={onEvidence} onRegenerate={onRegenerate} onSuggest={onSuggest} onOpenGuide={onOpenGuide} />
            </div>
          </div>
        ),
      )}
      {busy && (
        <div className="msg msg--ai" aria-label="답변을 만드는 중이에요">
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
              분석하는 중이에요
            </p>
          </div>
        </div>
      )}
      <div ref={endRef} />
    </div>
  )
}

function AiBubble({ m, onEvidence, onRegenerate, onSuggest, onOpenGuide }) {
  return (
    <>
      <div className={`bubble bubble--ai${m.kind === 'error' ? ' is-error' : ''}`}>
        {m.text && <p className="bubble__text">{m.text}</p>}

        {m.games && (
          <div className="amsg__games" aria-label="만든 번호">
            {m.games.map((g, i) => (
              <div key={i} className="amsg__game">
                <span className="amsg__name">{shortLabel(i)}</span>
                <div className="amsg__balls">
                  {g.map((n) => (
                    <LottoBall key={n} n={n} />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {m.kind === 'evidence' && (
          <>
            {m.basis && <p className="evi__basis">{m.basis}</p>}
            <dl className="evi">
              {m.items.map((it, i) => (
                <div key={i} className="evi__row">
                  <dt>{it.label}</dt>
                  <dd>{it.value}</dd>
                </div>
              ))}
            </dl>
            {m.references?.length > 0 && <p className="evi__ref">참고: {m.references.join(', ')}</p>}
          </>
        )}

        {m.footnote && <p className="bubble__foot">{m.footnote}</p>}
      </div>

      {(m.evidence || m.canRegenerate) && (
        <div className="msg__chips">
          {m.evidence && (
            <button type="button" className="chip chip--sm" onClick={() => onEvidence(m)}>
              통계 근거 보기
            </button>
          )}
          {m.canRegenerate && (
            <button type="button" className="chip chip--sm" onClick={() => onRegenerate(m)}>
              다시 생성
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

      {(m.suggestions?.length > 0 || m.action === 'OPEN_GUIDE') && (
        <div className="msg__chips">
          {m.suggestions?.map((s) => (
            <button key={s} type="button" className="chip chip--sm" onClick={() => onSuggest(s)}>
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
    </>
  )
}
