import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import LikeButton from '../features/like/LikeButton.jsx'
import HeroCard from '../features/home/HeroCard.jsx'
import HomeRail from '../features/home/HomeRail.jsx'
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
 * 모바일·태블릿에서는 홈 전체가 위아래 스크롤 없이 기기의 첫 화면에 딱 맞게 들어간다 (home.css 의 "첫 화면 한 번에 보이기").
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
        {/* 아래 카드 3장: 모바일은 옆으로 밀어 넘기는 줄, 태블릿·PC 는 원래 격자 (HomeRail 설명 참고) */}
        <HomeRail>
          <PrizeTrendCard trend={trend} />
          <RegionsCard q={regions} />
          <RookiesCard q={rookies} />
        </HomeRail>
        <div className="home__ad">
          <AdSlot />
        </div>
      </div>

      <NoticeBox />
    </div>
  )
}
