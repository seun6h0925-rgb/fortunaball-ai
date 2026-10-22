// 로또볼. 모든 번호가 같은 색이고 크기만 다르다. off=true 면 흐리게(미사용·불일치 표시).
// size(px)를 주면 그 크기, 안 주면 CSS 의 --s 값을 따른다. (화면 폭에 따라 크기가 변하는 곳에서 CSS 로 정할 수 있게)
export default function LottoBall({ n, size, off = false }) {
  return (
    <span className={`ball${off ? ' is-off' : ''}`} style={size ? { '--s': `${size}px` } : undefined}>
      {n}
    </span>
  )
}
