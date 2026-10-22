import { GUIDE_ITEMS } from './analystApi.js'

/**
 * "이렇게 물어보세요" 예시 목록. PC 왼쪽 카드와 사용법 창이 같이 쓴다.
 *  - showDesc : 예시 아래 설명을 보여 줄지 (PC 왼쪽 카드는 간단히 예시 문구만)
 *  - onPick   : 예시를 누르면 그 문구를 넘겨 준다 (입력창에 넣는다)
 */
export default function GuideContent({ showDesc = true, onPick }) {
  return (
    <ol className="guide">
      {GUIDE_ITEMS.map((g, i) => (
        <li key={g.title} className="guide__item">
          <span className="guide__no" aria-hidden="true">
            {i + 1}
          </span>
          <div className="guide__main">
            <b className="guide__title">{g.title}</b>
            <button type="button" className="guide__example" onClick={() => onPick(g.example)}>
              {g.example}
            </button>
            {showDesc && g.desc && <p className="guide__desc">{g.desc}</p>}
          </div>
        </li>
      ))}
    </ol>
  )
}
