import { Link } from 'react-router-dom'
import { EmptyState, ErrorState } from '../../components/ui/StateView.jsx'
import { formatDrawDate } from '../../lib/format.js'
import { fullWon } from '../draws/money.js'
import { RANKS } from './payoutRanks.js'

// 당첨금·세금·실수령액(UI-12)의 카드들. 금액·세율·기준일은 모두 서버(API-15)가 준 값 그대로 보여 준다.


/** 회차 고르기 아래의 등수 버튼 줄 */
export function RankPicker({ rank, onPick }) {
  return (
    <div className="pay__ranks" role="group" aria-label="등수 선택">
      {RANKS.map((r) => (
        <button key={r} type="button" className={`chip${r === rank ? ' is-on' : ''}`} aria-pressed={r === rank}
          onClick={() => onPick(r)}>
          {r}등
        </button>
      ))}
    </div>
  )
}

/** 결과 카드: 당첨금(1인 기준) · 실수령/세금 막대 · 세금 · 예상 실수령액 */
export function ResultCard({ q, round, rank }) {
  const d = q.data
  return (
    <section className={`card card--hero pay__result${q.loading ? ' is-loading' : ''}`} aria-label="계산 결과" aria-busy={q.loading}>
      {q.error ? (
        <ErrorState error={q.error} onRetry={q.reload} />
      ) : !d ? (
        <div className="skeleton" style={{ height: 220 }} />
      ) : d.prizeAmount <= 0 ? (
        <EmptyState title="당첨금 정보가 아직 없어요">
          <p>{round}회 {rank}등의 당첨금이 아직 집계되지 않았어요.</p>
        </EmptyState>
      ) : (
        <>
          <p className="pay__cap">{d.roundNo}회 · {d.rankLabel} 당첨금 · 1인 기준{d.winnerCount > 0 ? ` (당첨자 ${d.winnerCount.toLocaleString('ko-KR')}명)` : ''}</p>
          <p className="pay__prize">{fullWon(d.prizeAmount)}</p>
          <TaxBar prize={d.prizeAmount} net={d.netAmount} taxFree={d.taxFree} />
          <div className="pay__tiles">
            <div className="pay__tile">
              <span>세금</span>
              <b>{d.taxFree || d.totalTax <= 0 ? '없음 (비과세)' : `- ${fullWon(d.totalTax)}`}</b>
              {!d.taxFree && d.totalTax > 0 && (
                <small>소득세 {fullWon(d.incomeTax)} · 지방소득세 {fullWon(d.localIncomeTax)}</small>
              )}
            </div>
            <div className="pay__tile pay__tile--net">
              <span>예상 실수령액</span>
              <b>{fullWon(d.netAmount)}</b>
            </div>
          </div>
        </>
      )}
    </section>
  )
}

/** 실수령 / 세금 비율 막대 */
function TaxBar({ prize, net, taxFree }) {
  const netPct = taxFree ? 100 : Math.max(0, Math.min(100, (net / prize) * 100))
  const taxPct = 100 - netPct
  return (
    <div className="pay__bar-wrap">
      <div className="pay__bar" role="img" aria-label={`실수령액 ${netPct.toFixed(1)}퍼센트, 세금 ${taxPct.toFixed(1)}퍼센트`}>
        <span className="pay__bar-net" style={{ width: `${netPct}%` }} />
        <span className="pay__bar-tax" style={{ width: `${taxPct}%` }} />
      </div>
      <div className="pay__legend">
        <span><i className="pay__dot pay__dot--net" />실수령 {netPct.toFixed(1)}%</span>
        <span><i className="pay__dot pay__dot--tax" />세금 {taxPct.toFixed(1)}%</span>
      </div>
    </div>
  )
}

/** 계산 기준: 기준일·세율 문구·참고용 안내 */
export function BasisCard({ data }) {
  return (
    <section className="card pay__basis" aria-label="계산 기준">
      <b className="pay__basis-title">계산 기준</b>
      {data ? (
        <>
          <p>기준일 {formatDrawDate(data.policyDate) || data.policyDate}</p>
          <p>{data.rateLabel}</p>
          <div className="pay__basis-note">
            <p>동행복권에서 제시하는 세율을 적용한 계산이에요.</p>
            <p>참고용이라 실제 수령액은 지급 기관의 기준에 따라 다를 수 있어요.</p>
            <p>
              자세한 세율 안내는 <Link className="pay__link" to="/guide/payout">당첨금 수령 가이드</Link>의 링크에서 확인하실 수 있어요.
            </p>
          </div>
        </>
      ) : (
        <div className="skeleton" style={{ height: 64 }} />
      )}
    </section>
  )
}
