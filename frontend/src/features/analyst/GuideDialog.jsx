import { useEffect } from 'react'
import Icon from '../../components/ui/Icon.jsx'
import { GUIDE_DETAILS } from './analystApi.js'
import GuideContent from './GuideContent.jsx'

/**
 * 사용법 창. 모바일·태블릿은 아래에서 올라오는 시트, PC 는 가운데 창.
 * 예시를 누르면 입력창에 넣고 창을 닫는다. Esc·바깥 클릭·X 로도 닫힌다.
 */
export default function GuideDialog({ open, onClose, onPick }) {
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
    <div className="sheet" onClick={onClose}>
      <div className="sheet__box" role="dialog" aria-modal="true" aria-labelledby="guide-title" onClick={(e) => e.stopPropagation()}>
        <span className="sheet__grip hide-pc" aria-hidden="true" />
        <div className="sheet__head">
          <h2 id="guide-title" className="sheet__title">
            이렇게 물어보세요
          </h2>
          <button type="button" className="icon-btn sheet__close" onClick={onClose} aria-label="닫기">
            <Icon name="close" size={20} />
          </button>
        </div>
        <p className="sheet__lead">예시를 누르면 입력창에 바로 들어가요. 오늘 남은 무료 횟수는 대화창 위쪽에서 볼 수 있어요.</p>
        <GuideContent onPick={onPick} />

        <h3 className="sheet__sub">자세한 사용법</h3>
        <div className="gdet">
          {GUIDE_DETAILS.map((sec) => (
            <section key={sec.title} className="gdet__sec">
              <h4 className="gdet__title">{sec.title}</h4>
              <ul className="gdet__list">
                {sec.lines.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </section>
          ))}
        </div>
        <p className="sheet__note">
          통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.
        </p>
      </div>
    </div>
  )
}
