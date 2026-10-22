import LottoBall from '../../components/ui/LottoBall.jsx'
import { gameLabel } from './gameLabel.js'

/**
 * 번호 카드: 게임마다 한 줄씩 [게임 이름 + 공 6개]. 결과 화면(UI-03)과 발급 완료 화면(UI-03B)이 함께 쓴다.
 *  - games : 번호 6개짜리 배열들의 배열    - meta : PC 에서 카드 제목 줄 오른쪽에 보이는 요약 글
 */
export default function GameList({ games, meta }) {
  return (
    <section className="card res__games" aria-label="번호 목록">
      <div className="res__head only-pc">
        <h2>생성된 번호</h2>
        <span className="sub">{meta}</span>
      </div>
      <ul className="res__list">
        {games.map((numbers, i) => (
          <li key={i} className="res__game">
            <span className="res__name">{gameLabel(i)}</span>
            <div className="res__balls" aria-label={`${gameLabel(i)} ${numbers.join(', ')}`}>
              {numbers.map((n) => (
                <LottoBall key={n} n={n} />
              ))}
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
