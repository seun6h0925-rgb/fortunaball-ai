import Icon from '../../components/ui/Icon.jsx'
import { useToast } from '../toast/toastContext.js'

/**
 * 공유하기 버튼. 휴대폰처럼 공유 창을 쓸 수 있으면 그 창을 열고,
 * 아니면 주소를 복사하고 "복사했어요" 알림을 보여준다.
 */
export default function ShareButton({ variant = 'compact', title }) {
  const toast = useToast()

  async function onShare() {
    const url = window.location.href
    const data = { title: title || document.title, url }
    try {
      if (navigator.share) {
        await navigator.share(data)
        return
      }
      await navigator.clipboard.writeText(url)
      toast('주소를 복사했어요. 원하는 곳에 붙여넣어 보내 주세요.')
    } catch (e) {
      if (e?.name === 'AbortError') return // 사용자가 공유 창을 닫은 경우
      toast('공유하지 못했어요. 주소창의 주소를 직접 복사해 주세요.')
    }
  }

  return (
    <button type="button" className={`share-btn${variant === 'block' ? ' share-btn--block' : ''}`} onClick={onShare}>
      <Icon name="share" size={16} color="#7CC8FC" strokeWidth={2.2} />
      공유하기
    </button>
  )
}
