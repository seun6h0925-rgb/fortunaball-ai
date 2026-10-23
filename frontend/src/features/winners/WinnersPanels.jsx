import { useState } from 'react'
import IssueCard from '../issues/IssueCard.jsx'
import { fetchIssueDetail } from '../issues/issuesApi.js'
import { CODE_LENGTH, normalizeCode } from '../../lib/issueCode.js'
import { formatDateTimeSec } from '../../lib/format.js'
import { matchText } from './winnersApi.js'

// 우리 서비스 당첨 기록(UI-09)의 카드들.

/** 위쪽 요약 3칸: 누적 기록 / 3등 이상 / 최근 회차 (모두 서버가 준 건수·목록에서 읽는다) */
export function SummaryTiles({ total, topCount, latestRound }) {
  const items = [
    ['누적 기록', total == null ? '-' : `${total.toLocaleString('ko-KR')}건`],
    ['3등 이상', topCount == null ? '-' : `${topCount.toLocaleString('ko-KR')}건`],
    ['최근 회차', latestRound ? `${latestRound}회` : '-'],
  ]
  return (
    <div className="win__tiles">
      {items.map(([label, value]) => (
        <div key={label} className="card card--hero win__tile">
          <span>{label}</span>
          <b>{value}</b>
        </div>
      ))}
    </div>
  )
}

/**
 * 발급코드로 상세 조회. 코드만 알면 내 발급 번호의 검증 결과를 볼 수 있다. (API-05, 조회 횟수 제한이 있어 형식이 틀리면 서버에 묻지 않는다)
 * 결과 카드는 발급 내역 화면(UI-04)과 같은 IssueCard 를 쓴다.
 */
export function CodeLookup() {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [found, setFound] = useState(null)

  async function onSubmit(e) {
    e.preventDefault()
    if (busy) return
    const normalized = normalizeCode(code)
    if (normalized.length !== CODE_LENGTH) {
      setError(`발급 코드는 ${CODE_LENGTH}자리예요. 다시 확인해 주세요.`)
      return
    }
    setBusy(true)
    setError('')
    try {
      setFound(await fetchIssueDetail(normalized))
    } catch (err) {
      setFound(null)
      setError(err?.message || '조회하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card card--hero win__lookup" aria-label="발급코드로 상세 조회">
      <h2 className="win__title">발급코드로 상세 조회</h2>
      <form className="win__lookup-row" onSubmit={onSubmit} noValidate>
        <input className="gen__input win__input" type="text" autoComplete="off" autoCapitalize="characters"
          spellCheck={false} maxLength={20} placeholder="발급 코드를 입력하세요" aria-label="발급 코드"
          value={code}
          onChange={(e) => {
            setCode(e.target.value)
            setError('')
          }} />
        <button type="submit" className="btn btn--cta win__go" disabled={busy}>
          {busy ? <span className="spinner spinner--sm" aria-hidden="true" /> : '조회'}
        </button>
      </form>
      {error && <p className="gen__error" role="alert">{error}</p>}
      {found && (
        <IssueCard item={found} games={found.games.map((g) => ({ order: g.gameOrder, numbers: g.numbers }))} />
      )}
    </section>
  )
}

/** 공개된 당첨 기록 한 건. 회차·등수·일치 개수와 함께, 누가·언제 받은 어떤 코드인지(발급코드·게스트·발급 시각)를 그대로 보여 준다. */
export function RecordRow({ item }) {
  return (
    <li className="win__rec">
      <div className="win__rec-top">
        <b className="win__rec-round">{item.roundNo}회</b>
        <span className={`win__rank win__rank--${item.rank}`}>{item.rank}등</span>
        <span className="win__rec-match">{matchText(item)}</span>
      </div>
      <p className="win__rec-meta">
        <span>발급코드 <b className="win__rec-code">{item.issueCode}</b></span>
        <span>{item.guestMasked}</span>
        <span>{formatDateTimeSec(item.issuedAt)} 발급</span>
      </p>
      <span className="win__rec-date">{formatDateTimeSec(item.verifiedAt)} 확인</span>
      {item.comment && <p className="win__rec-comment">“{item.comment}”</p>}
    </li>
  )
}

/** 승인된 익명 인증의 한마디 (한마디가 있는 기록만) */
export function CommentList({ items }) {
  return (
    <section className="card card--hero win__comments" aria-label="승인된 익명 인증">
      <h2 className="win__title">승인된 익명 인증</h2>
      {items.length === 0 ? (
        <p className="win__empty">아직 공개된 한마디가 없어요.</p>
      ) : (
        <ul className="win__list">
          {items.map((it) => (
            <li key={it.recordId} className="win__cmt">
              <span className="win__cmt-head">{it.roundNo}회 · {it.rank}등</span>
              <p>“{it.comment}”</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
