import { useEffect } from 'react'
import Icon from '../../components/ui/Icon.jsx'
import GuideContent from './GuideContent.jsx'

/**
 * 사용법 창 (AI 애널리스트·꿈풀이 공용). 모바일·태블릿은 아래에서 올라오는 시트, PC 는 가운데 창.
 *  - items   : 예시 목록(GuideContent 와 같은 모양)
 *  - details : [{ title, lines[] }] 자세한 사용법. 없으면 생략한다.
 *  - lead    : 제목 아래 안내 문장
 *  - note    : 맨 아래 고정 안내
 * 예시를 누르면 입력창에 넣고 창을 닫는다. Esc·바깥 클릭·X 로도 닫힌다.
 */
export default function GuideDialog({ open, onClose, onPick, items, details, lead, note }) {
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
        <p className="sheet__lead">{lead}</p>
        <GuideContent items={items} onPick={onPick} />

        {details && (
          <>
            <h3 className="sheet__sub">자세한 사용법</h3>
            <div className="gdet">
              {details.map((sec) => (
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
          </>
        )}
        <p className="sheet__note">{note}</p>
      </div>
    </div>
  )
}
