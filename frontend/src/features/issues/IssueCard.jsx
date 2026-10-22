import { Link } from 'react-router-dom'
import LottoBall from '../../components/ui/LottoBall.jsx'
import { formatDateTime } from '../../lib/format.js'
import { groupCode } from '../../lib/issueCode.js'
import { useToast } from '../toast/toastContext.js'
import { gameLabel } from '../generate/gameLabel.js'

/**
 * 발급 내역 카드 한 장.
 *  - item  : 회차·발급시간·게임 수·검증 상태·발급 코드 (목록 항목과 상세 조회 결과가 같은 이름을 써서 둘 다 넣을 수 있다)
 *  - games : 보여 줄 게임들 [{ order, numbers }]. 목록은 첫 게임 하나, 코드 조회 결과는 전체 게임.
 *            2개 이상이면 줄마다 "게임 A" 이름을 붙인다.
 */
export default function IssueCard({ item, games }) {
  const toast = useToast()

  async function copy() {
    try {
      await navigator.clipboard.writeText(item.issueCode)
      toast('발급 코드를 복사했어요.')
    } catch {
      toast('복사하지 못했어요. 코드를 길게 눌러 직접 복사해 주세요.')
    }
  }

  const failed = item.verificationStatus === 'ERROR'
  return (
    <article className="card icard">
      <div className="icard__info">
        <div className="icard__head">
          <h3 className="icard__round">{item.targetRound}회</h3>
          <span className={`icard__badge${failed ? ' is-error' : ''}`}>{item.verificationLabel}</span>
        </div>
        <p className="icard__meta">
          {formatDateTime(item.issuedAt)} 발급 · {item.gameCount}게임
          {item.patternName ? ` · ${item.patternName}` : ''}
        </p>
      </div>

      <div className="icard__games">
        {games.map((g) => (
          <div key={g.order} className="icard__game">
            {games.length > 1 && <span className="icard__name">{gameLabel(g.order - 1)}</span>}
            <div className="res__balls icard__balls" aria-label={`${gameLabel(g.order - 1)} ${g.numbers.join(', ')}`}>
              {g.numbers.map((n) => (
                <LottoBall key={n} n={n} />
              ))}
            </div>
          </div>
        ))}
      </div>

      <div className="icard__foot">
        <span className="icard__code">
          <span className="sub">코드</span> {groupCode(item.issueCode)}
        </span>
        <div className="icard__btns">
          <button type="button" className="btn btn--ghost" onClick={copy}>
            코드 복사
          </button>
          {/* 분석 보기: AI 애널리스트(UI-05)가 이 발급 코드의 번호를 분석한다 */}
          <Link to={`/analyst?code=${item.issueCode}`} className="btn btn--ghost">
            분석 보기
          </Link>
        </div>
      </div>
    </article>
  )
}
