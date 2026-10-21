// 화면 맨 아래 "유의사항" 상자. 고정 안내 문구는 화면마다 여기에 한 번만 쓴다.
export default function NoticeBox({ children }) {
  return (
    <div className="card note" style={{ borderRadius: 16 }}>
      <b className="note__title">유의사항</b>
      {children ?? <div>통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.</div>}
    </div>
  )
}
