// 광고(애드센스) 자리. 높이를 고정해 광고가 늦게 떠도 화면이 밀리지 않는다.
// TODO: 애드센스 승인 후 이 안에 광고 코드를 넣는다. (지금은 목업과 같은 안내 칸)
export default function AdSlot() {
  return (
    <div className="ad-slot" role="complementary" aria-label="광고">
      <span className="ad-slot__tag">광고</span>
      배너 광고 영역 (고정 높이)
    </div>
  )
}
