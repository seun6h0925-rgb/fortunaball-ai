import LikeButton from '../../features/like/LikeButton.jsx'
import ShareButton from '../../features/like/ShareButton.jsx'

/**
 * 화면 제목 줄.
 *  - share=true  : 공유하기가 있는 화면. PC 는 제목 줄 오른쪽에 [공유하기][좋아요],
 *                  모바일·태블릿은 화면 아래 <ShareLikeBar/> 에 둔다.
 *  - share=false : 공유하기가 없는 화면. 좋아요 버튼이 공유하기 자리(제목 줄 오른쪽)에 온다.
 *  - actions=false: 제목 줄에 좋아요·공유 버튼을 두지 않는다. (버튼이 화면 안쪽 카드에 따로 있는 화면)
 *  - tagline=true: "결과는 알 수 없어도, 선택은 스마트하게." 문구를 보여준다.
 */
export default function PageHeader({ title, subtitle, tagline = false, share = false, actions = true, children }) {
  return (
    <header className="phead">
      <div className="phead__row">
        <h1 className="phead__title">{title}</h1>
        {tagline && (
          <p className="phead__tagline only-pc">
            결과는 알 수 없어도, <b>선택은 스마트하게.</b>
          </p>
        )}
        {!actions ? null : share ? (
          <div className="phead__actions only-pc">
            <ShareButton />
            <LikeButton />
          </div>
        ) : (
          <div className="phead__actions">
            <LikeButton variant="compact" />
          </div>
        )}
      </div>
      {tagline && (
        <p className="phead__tagline hide-pc">
          결과는 알 수 없어도, <b>선택은 스마트하게.</b>
        </p>
      )}
      {subtitle && <p className="phead__sub">{subtitle}</p>}
      {children}
    </header>
  )
}

/** 모바일·태블릿 화면 아래쪽 [공유하기][좋아요] 줄. PC 에서는 보이지 않는다. */
export function ShareLikeBar() {
  return (
    <div className="share-bar hide-pc">
      <ShareButton variant="block" />
      <LikeButton variant="block" />
    </div>
  )
}
