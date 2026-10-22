import Icon from '../../components/ui/Icon.jsx'
import { useToast } from '../toast/toastContext.js'

/**
 * 공유하기 버튼. url 을 주면 그 주소를, 안 주면 지금 화면 주소를 공유한다.
 * (번호 결과처럼 이 기기에만 있는 화면은 서비스 주소를 넘긴다) 휴대폰처럼 공유 창을 쓸 수 있으면 그 창을 열고,
 * 아니면 주소를 복사하고 "복사했어요" 알림을 보여준다.
 */
export default function ShareButton({ variant = 'compact', title, url: shareUrl }) {
  const toast = useToast()

  async function onShare() {
    const url = shareUrl || window.location.href
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
