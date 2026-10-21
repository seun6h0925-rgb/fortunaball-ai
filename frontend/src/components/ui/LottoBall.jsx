// 로또볼. 모든 번호가 같은 색이고 크기(size)만 다르다. off=true 면 흐리게(미사용·불일치 표시).
export default function LottoBall({ n, size = 40, off = false }) {
  return (
    <span className={`ball${off ? ' is-off' : ''}`} style={{ '--s': `${size}px` }}>
      {n}
    </span>
  )
}
