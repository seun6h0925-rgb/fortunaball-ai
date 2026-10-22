import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import LikeButton from '../features/like/LikeButton.jsx'
import HeroCard from '../features/home/HeroCard.jsx'
import PrizeTrendCard from '../features/home/PrizeTrendCard.jsx'
import RegionsCard from '../features/home/RegionsCard.jsx'
import RookiesCard from '../features/home/RookiesCard.jsx'
import {
  fetchCurrentRound,
  fetchLatestDraw,
  fetchPrizeTrend,
  fetchRegionStats,
  fetchRookieStores,
} from '../features/home/homeApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'

/**
 * 홈(UI-01). 서버 기능 5개를 한꺼번에 요청하고, 카드마다 따로 보여 준다.
 * 어느 하나가 실패해도 다른 카드는 정상으로 보이고, 실패한 카드만 "다시 시도"를 보여 준다.
 * 최근 20회 당첨금 추이(trend)는 맨 위 카드의 "전 회차 대비"와 그래프가 같이 쓴다.
 */
export default function HomePage() {
  usePageTitle('')
  const latest = useApi(fetchLatestDraw, [])
  const current = useApi(fetchCurrentRound, [])
  const trend = useApi((signal) => fetchPrizeTrend(signal), [])
  const regions = useApi(fetchRegionStats, [])
  const rookies = useApi((signal) => fetchRookieStores(signal, 4), [])

  return (
    <div className="home">
      {/* PC 는 제목 줄이 없어서 좋아요 버튼이 오른쪽 위에 온다. 모바일·태블릿은 맨 위 로고 줄에 있다 */}
      <div className="home__likebar only-pc">
        <LikeButton />
      </div>

      <div className="home__grid">
        <HeroCard latest={latest} current={current} trend={trend} />
        <PrizeTrendCard trend={trend} />
        <RegionsCard q={regions} />
        <RookiesCard q={rookies} />
        <div className="home__ad">
          <AdSlot />
        </div>
      </div>

      <NoticeBox />
    </div>
  )
}
