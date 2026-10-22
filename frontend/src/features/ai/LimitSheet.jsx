import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useToast } from '../toast/toastContext.js'

/**
 * 오늘 무료 AI 횟수를 모두 쓴 뒤 보여 주는 안내 창 (AI 애널리스트·꿈풀이 공용, 서버 오류 AI-004 때 연다).
 * 모바일은 아래에서 올라오는 시트, PC 는 가운데 창. 횟수와 상관없는 다른 기능으로 이어 주는 길을 보여 준다.
 * 후원하기·이용권·광고는 아직 기능이 없어서 "준비 중"으로 안내한다.
 */
export default function LimitSheet({ open, onClose }) {
  const toast = useToast()

  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return (
    <div className="sheet sheet--limit" onClick={onClose}>
      <div className="sheet__box" role="dialog" aria-modal="true" aria-labelledby="limit-title" onClick={(e) => e.stopPropagation()}>
        <span className="sheet__grip hide-pc" aria-hidden="true" />
        <h2 id="limit-title" className="sheet__title">
          오늘 무료 횟수를 모두 사용했어요
        </h2>
        <p className="sheet__lead">
          무료 횟수는 매일 0시에 다시 채워져요. 번호 생성, 통계 분석, 판매점 지도 같은 다른 기능은 계속 이용할 수 있어요.
        </p>
        <div className="limit__btns">
          <Link to="/generate" className="btn btn--cta btn--block" onClick={onClose}>
            통계 번호 생성하러 가기
          </Link>
          <button type="button" className="btn btn--ghost btn--block limit__ghost" onClick={() => toast('후원하기는 준비 중이에요.')}>
            후원하기
          </button>
          <div className="limit__soon">
            <span className="limit__tag">준비 중</span>
            이용권으로 횟수 충전
          </div>
          <div className="limit__soon">
            <span className="limit__tag">준비 중</span>
            광고 보고 횟수 받기
          </div>
        </div>
        <p className="sheet__note">
          AI 호출에는 서버 비용이 들어서 하루 무료 횟수를 두고 있어요. 통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.
        </p>
        <button type="button" className="limit__close" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  )
}
