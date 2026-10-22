import { useState } from 'react'
import LottoBall from '../../components/ui/LottoBall.jsx'
import { formatDrawDate } from '../../lib/format.js'
import useApi from '../../hooks/useApi.js'
import { RoundSheet } from '../../components/ui/RoundNav.jsx'
import { RANK_RULES, TAXED_RANKS, calcPayout } from './drawsApi.js'
import { fullWon, people, shortWon } from './money.js'

// 회차별 당첨번호(UI-08)의 두 카드. 모든 금액·인원은 서버(API-01·02·15)가 준 값 그대로 보여 준다.

/**
 * 회차 카드: ‹ 회차 ▾ › 와 당첨번호·보너스.
 * 가운데 회차를 누르면 선택 창(회차 입력·날짜·목록)이 열린다. (공용 RoundSheet)
 */
export function RoundCard({ draw, latest, onPick }) {
  const [open, setOpen] = useState(false)
  const round = draw?.roundNo
  return (
    <section className="card card--hero draws__round" aria-label="회차 당첨번호">
      <div className="draws__nav">
        <button type="button" className="draws__arrow" aria-label="이전 회차" disabled={!round || round <= 1}
          onClick={() => onPick(round - 1)}>
          ‹
        </button>
        <button type="button" className="draws__title" aria-haspopup="dialog" disabled={!round} onClick={() => setOpen(true)}>
          <b>
            {round ? `${round}회차` : '…'}
            <span className="draws__caret" aria-hidden="true"> ▾</span>
          </b>
          <span className="draws__date">{draw ? `${formatDrawDate(draw.drawDate)} 추첨` : ' '}</span>
        </button>
        <button type="button" className="draws__arrow" aria-label="다음 회차" disabled={!round || round >= latest}
          onClick={() => onPick(round + 1)}>
          ›
        </button>
      </div>

      {draw ? (
        <div className="draws__balls" aria-label={`당첨번호 ${draw.numbers.join(', ')} 보너스 ${draw.bonusNumber}`}>
          {draw.numbers.map((n) => (
            <LottoBall key={n} n={n} />
          ))}
          <span className="draws__plus" aria-hidden="true">+</span>
          <LottoBall n={draw.bonusNumber} />
        </div>
      ) : (
        <div className="skeleton draws__balls-skel" />
      )}
      <p className="draws__hint">마지막 번호는 보너스 번호예요</p>

      {open && <RoundSheet value={round} latest={latest} first={1} latestTag="최신"
        onClose={() => setOpen(false)}
        onPick={(r) => {
          setOpen(false)
          onPick(r)
        }} />}
    </section>
  )
}

/** 등수별 당첨 결과 표. 1~3등은 눌러서 세금·실수령액을 펼친다. (처음에는 1등이 열려 있다) */
export function PrizeTable({ draw }) {
  const [openRank, setOpenRank] = useState(1)
  const prizes = [...(draw?.prizes ?? [])].sort((a, b) => a.winningRank - b.winningRank)
  return (
    <section className="card card--hero draws__prizes" aria-label="등수별 당첨 결과">
      <div className="draws__phead">
        <h2 className="draws__ptitle">등수별 당첨 결과</h2>
        <span className="draws__psub">1~3등을 누르면 세금·실수령액</span>
      </div>
      <div className="draws__row draws__row--head" aria-hidden="true">
        <span>등수</span><span>조건</span><span className="r">1인당 당첨금</span><span className="r">당첨자 수</span><span />
      </div>
      {prizes.length === 0 && <p className="draws__empty">이 회차의 당첨금 정보가 아직 없어요.</p>}
      {prizes.map((p) => {
        const taxed = TAXED_RANKS.includes(p.winningRank)
        const open = taxed && openRank === p.winningRank
        const Row = taxed ? 'button' : 'div'
        return (
          <div key={p.winningRank} className={`draws__item${open ? ' is-open' : ''}`}>
            <Row className="draws__row" {...(taxed ? { type: 'button', 'aria-expanded': open, onClick: () => setOpenRank(open ? null : p.winningRank) } : {})}>
              <span className="draws__rank">{p.winningRank}등</span>
              <span className="draws__cond">{RANK_RULES[p.winningRank]}</span>
              <span className="r">{shortWon(p.prizeAmount)}</span>
              <span className="r">{people(p.winnerCount)}</span>
              <span className="draws__chev" aria-hidden="true">{taxed ? (open ? '⌃' : '⌄') : ''}</span>
            </Row>
            {open && <TaxDetail roundNo={draw.roundNo} prize={p} />}
          </div>
        )
      })}
    </section>
  )
}

/** 펼쳤을 때만 세금 계산(API-15)을 부른다. 서버가 계산한 값을 그대로 보여 준다. */
function TaxDetail({ roundNo, prize }) {
  const q = useApi((signal) => calcPayout(signal, roundNo, prize.winningRank), [roundNo, prize.winningRank])
  if (q.error) {
    return (
      <div className="draws__tax">
        <p className="draws__empty">세금 계산을 불러오지 못했어요.</p>
        <button type="button" className="chip chip--sm" onClick={q.reload}>다시 시도</button>
      </div>
    )
  }
  if (!q.data) return <div className="draws__tax"><div className="skeleton" style={{ height: 96 }} /></div>
  const t = q.data
  return (
    <div className="draws__tax">
      <div className="draws__taxgrid">
        <div className="draws__taxcol">
          <div className="draws__line">
            <span>{prize.winningRank}등 총 당첨금액<small>1인 당첨금 × 당첨자 {prize.winnerCount.toLocaleString('ko-KR')}명</small></span>
            <b>{fullWon(t.prizeAmount * t.winnerCount)}</b>
          </div>
          <div className="draws__line"><span>1인 당첨금</span><b>{fullWon(t.prizeAmount)}</b></div>
        </div>
        <div className="draws__taxcol">
          <div className="draws__line">
            <span>세금<small>{t.taxFree ? '비과세' : t.rateLabel}</small></span>
            <b>{t.taxFree ? '없음' : `- ${fullWon(t.totalTax)}`}</b>
          </div>
          <div className="draws__line draws__line--net"><span>1인 실수령액</span><b>{fullWon(t.netAmount)}</b></div>
        </div>
      </div>
      <p className="draws__taxnote">{t.notice || '참고용 계산이에요. 실제 세금은 공식 안내를 확인하세요.'}</p>
    </div>
  )
}
