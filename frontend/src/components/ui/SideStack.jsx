import { useToast } from '../../features/toast/toastContext.js'

/**
 * PC 화면에서 비는 칸에 넣는 후원하기 줄.
 *  - 모바일·태블릿(폭 1024px 미만)에서는 CSS 로 숨긴다. (이미 한 줄로 쌓여서 빈칸이 없다)
 *  - 후원 기능은 아직 준비 중이라 안내 문구만 띄운다.
 */
export default function SideStack() {
  const toast = useToast()
  return (
    <div className="sidec">
      <div className="card card--hero sidec__support">
        <div className="sidec__support-text">
          <b>서비스가 마음에 드셨나요?</b>
          <small>후원으로 운영을 도와주실 수 있어요.</small>
        </div>
        <button type="button" className="btn btn--ghost sidec__support-btn" onClick={() => toast('후원 기능은 준비 중이에요.')}>
          후원하기
        </button>
      </div>
    </div>
  )
}
