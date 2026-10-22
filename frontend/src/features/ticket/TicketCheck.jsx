import { useEffect, useRef, useState } from 'react'
import Icon from '../../components/ui/Icon.jsx'
import LottoBall from '../../components/ui/LottoBall.jsx'
import { ErrorState } from '../../components/ui/StateView.jsx'
import { formatDrawDate } from '../../lib/format.js'
import { readQrFromFile } from './readQr.js'
import { checkTicket } from './ticketApi.js'

// 내 용지 확인하기(UI-08): 로또 용지 QR 사진으로 당첨 여부를 바로 확인한다.

/** 회차 카드 아래 "내 용지 확인하기" 카드 */
export function TicketCard() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" className="card card--hero tkc" onClick={() => setOpen(true)} aria-haspopup="dialog">
        <span className="tkc__ico" aria-hidden="true"><Icon name="image" size={24} /></span>
        <span className="tkc__text">
          <b>내 용지 확인하기</b>
          <small>로또 용지 QR을 찍으면 당첨 여부를 바로 알려줘요</small>
        </span>
        <Icon name="chevronRight" size={20} />
      </button>
      {open && <TicketSheet onClose={() => setOpen(false)} />}
    </>
  )
}

/** 사진 고르기 → QR 읽기 → 서버 확인 → 결과. 사진은 이 기기 안에서만 읽고 서버로 보내지 않는다. */
function TicketSheet({ onClose }) {
  const fileRef = useRef(null)
  const [phase, setPhase] = useState('idle') // idle | working | done | error
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  async function onFile(e) {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 사진을 다시 골라도 다시 읽게 비운다
    if (!file) return
    setPhase('working')
    setError(null)
    try {
      const text = await readQrFromFile(file)
      if (!text) {
        setError(new Error('사진에서 QR을 찾지 못했어요. QR이 크고 선명하게 보이도록 용지만 가까이서 다시 찍어 주세요.'))
        setPhase('error')
        return
      }
      setResult(await checkTicket(undefined, text))
      setPhase('done')
    } catch (err) {
      setError(err)
      setPhase('error')
    }
  }

  return (
    <div className="sheet" onClick={onClose}>
      <div className="sheet__box" role="dialog" aria-modal="true" aria-labelledby="tk-title" onClick={(e) => e.stopPropagation()}>
        <span className="sheet__grip hide-pc" aria-hidden="true" />
        <div className="sheet__head">
          <h2 id="tk-title" className="sheet__title">내 용지 확인하기</h2>
          <button type="button" className="icon-btn sheet__close" onClick={onClose} aria-label="닫기"><Icon name="close" size={20} /></button>
        </div>

        <input ref={fileRef} type="file" accept="image/*" capture="environment" hidden onChange={onFile} />

        {phase !== 'done' && (
          <div className="tk__pick">
            <p className="sheet__lead">로또 용지의 QR이 잘 보이게 사진을 찍거나 골라 주세요.</p>
            <ul className="tk__tips">
              <li>용지를 평평하게 펴고 QR만 크게 나오게 찍어요.</li>
              <li>빛이 반사되지 않게 해 주세요.</li>
            </ul>
            <button type="button" className="btn btn--cta btn--block" disabled={phase === 'working'} onClick={() => fileRef.current?.click()}>
              {phase === 'working' ? '확인하는 중이에요…' : '사진 찍기 · 사진 고르기'}
            </button>
            <p className="sheet__note">사진은 서버로 보내지 않아요. 이 기기에서 읽은 QR 글자만 전달하고 저장하지 않아요.</p>
            {phase === 'error' && <ErrorState error={error} />}
          </div>
        )}

        {phase === 'done' && result && (
          <>
            <TicketResult r={result} />
            <button type="button" className="btn btn--ghost btn--block" onClick={() => { setPhase('idle'); setResult(null) }}>다른 용지 확인하기</button>
          </>
        )}
      </div>
    </div>
  )
}

/** 결과: 최고 등수·안내 문구·당첨번호·줄별 번호(맞힌 번호는 밝게) */
function TicketResult({ r }) {
  const won = r.bestRank != null
  const hit = new Set(r.winningNumbers ?? [])
  return (
    <div className="tk__res" aria-live="polite">
      <div className={`tk__best${won ? ' is-win' : ''}`}>
        <small>{r.roundNo}회 용지{r.drawDate ? ` · ${formatDrawDate(r.drawDate)} 추첨` : ''}</small>
        <b>{r.drawn ? r.bestRankLabel : '아직 추첨 전이에요'}</b>
        <p>{r.message}</p>
      </div>
      {r.drawn && r.winningNumbers && (
        <div className="tk__win">
          <span>당첨번호</span>
          <div className="tk__balls">
            {r.winningNumbers.map((n) => <LottoBall key={n} n={n} size={30} />)}
            <i aria-hidden="true">+</i>
            <LottoBall n={r.bonusNumber} size={30} />
          </div>
        </div>
      )}
      <ul className="tk__games">
        {r.games.map((g) => (
          <li key={g.line} className="tk__game">
            <span className="tk__line">{g.line}<small>{g.kindLabel}</small></span>
            <span className="tk__balls">
              {g.numbers.map((n) => (
                <LottoBall key={n} n={n} size={30} off={r.drawn && !(g.matchedNumbers ?? []).includes(n) && !(hit.has(n))} />
              ))}
            </span>
            <b className={`tk__rank${g.rank ? ' is-win' : ''}`}>{r.drawn ? g.rankLabel : '-'}</b>
          </li>
        ))}
      </ul>
    </div>
  )
}
