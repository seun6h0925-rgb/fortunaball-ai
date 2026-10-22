import PageHeader, { ShareLikeBar } from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import InfoTabs from '../features/info/InfoTabs.jsx'
import usePageTitle from '../hooks/usePageTitle.js'

// 당첨금 수령 가이드(UI-13). 고정 안내 화면이라 글을 이 파일에 둔다. (바꿀 때는 아래 값만 고친다)

const PLACES = [
  { rank: '1~2등', where: 'NH농협은행 본점', text: '고액 당첨은 본점에서 수령해요. 방문 전 공식 안내에서 영업시간과 예약 여부를 확인하세요.' },
  { rank: '3등', where: 'NH농협은행 전국 지점', text: '가까운 지점에서 수령할 수 있어요.' },
  { rank: '4~5등', where: '복권 판매점', text: '판매점에서 바로 수령하거나 다른 복권으로 교환할 수 있어요.' },
]
const STEPS = [
  { title: '복권 QR 등록', text: '종이 복권의 QR을 페이코 앱에서 읽어 등록해요.' },
  { title: '당첨 알림', text: '추첨 후 앱이 당첨 여부를 알려 줘요.' },
  { title: '자동 입금', text: '지급 기한까지 직접 받지 않은 당첨금이 자동으로 입금돼요.' },
]
const AUTO_NOTE = [
  '200만원 이하는 앱 포인트로, 200만원 초과는 미리 등록한 본인 계좌(실명 확인)로 입금된다고 안내돼 있어요.',
  '종이 복권은 지급 기한까지 꼭 보관하세요.',
  '다른 사람이 먼저 판매점에서 수령하면 앱으로는 받을 수 없어요.',
  '지원 앱과 대상 등수는 달라질 수 있어요. 더 자세한 내용은 아래 공식 안내에서 확인하세요.',
]
// 더 자세한 내용(수령처·준비물·지급기한·세금)은 동행복권 "당첨자 가이드"에서 확인한다
const GUIDE_URL = 'https://www.dhlottery.co.kr/guide/wnrGuide'
const PREPARE = ['당첨 복권 원본 (훼손 금지)', '신분증', '본인 서명']
const CAUTIONS = ['당첨 사실을 함부로 알리지 마세요', '복권 뒷면에 서명하고 안전하게 보관하세요', '지급 기한이 지나면 받을 수 없어요', '수령 전 공식 안내를 확인하세요']

/**
 * 당첨금 수령 가이드(UI-13). 모바일은 등수별 수령처부터 위에서 아래로, PC 는 자동 입금 카드가 맨 위에 넓게 온다.
 * 모바일 위쪽의 칩은 해당 카드로 바로 이동하는 링크다.
 */
export default function ClaimGuidePage() {
  usePageTitle('당첨금 수령 가이드')
  return (
    <div className="gd">
      <PageHeader title="당첨금 수령 가이드" tagline share>
        <p className="phead__sub">당첨되면 어디서 어떻게 받는지 알려드려요</p>
        <InfoTabs group="calc" />
        <nav className="chip-row hide-pc" aria-label="이 화면의 항목">
          <a className="chip" href="#gd-places">등수별 수령처</a>
          <a className="chip" href="#gd-auto">앱 자동 입금</a>
          <a className="chip" href="#gd-prep">준비물</a>
          <a className="chip" href="#gd-warn">고액 당첨 주의</a>
        </nav>
      </PageHeader>

      <div className="gd__grid">
        <section id="gd-auto" className="card card--hero gd__auto" aria-labelledby="gd-auto-t">
          <div className="gd__head">
            <span className="gd__ico" aria-hidden="true"><Icon name="sparkle" size={20} /></span>
            <h2 id="gd-auto-t" className="gd__title">앱에 등록하면 방문 없이 자동 입금</h2>
            <span className="gd__badge">2026.08.18 시작</span>
          </div>
          <ol className="gd__steps">
            {STEPS.map((s, i) => (
              <li key={s.title} className="gd__step">
                <span className="gd__no">{i + 1}</span>
                <div><b>{s.title}</b><p>{s.text}</p></div>
              </li>
            ))}
          </ol>
          <div className="gd__note">
            {AUTO_NOTE.map((t) => <p key={t}>{t}</p>)}
          </div>
          <a className="chip gd__link" href={GUIDE_URL} target="_blank" rel="noopener noreferrer">
            동행복권 당첨자 가이드 바로가기 ↗
          </a>
        </section>

        <section id="gd-places" className="card card--hero gd__places" aria-labelledby="gd-places-t">
          <div className="gd__head">
            <span className="gd__ico" aria-hidden="true"><Icon name="pin" size={20} /></span>
            <h2 id="gd-places-t" className="gd__title">등수별 수령처</h2>
          </div>
          <ul className="gd__list">
            {PLACES.map((p) => (
              <li key={p.rank} className="gd__item">
                <b><em>{p.rank}</em> {p.where}</b>
                <p>{p.text}</p>
              </li>
            ))}
          </ul>
        </section>

        <section id="gd-prep" className="card card--hero gd__prep" aria-labelledby="gd-prep-t">
          <div className="gd__head">
            <span className="gd__ico" aria-hidden="true"><Icon name="check" size={20} /></span>
            <h2 id="gd-prep-t" className="gd__title">준비물</h2>
          </div>
          <ul className="gd__list">
            {PREPARE.map((t) => (
              <li key={t} className="gd__item gd__item--check"><Icon name="check" size={16} />{t}</li>
            ))}
          </ul>
        </section>

        <section id="gd-warn" className="card card--hero gd__warn" aria-labelledby="gd-warn-t">
          <div className="gd__head">
            <span className="gd__ico" aria-hidden="true"><Icon name="info" size={20} /></span>
            <h2 id="gd-warn-t" className="gd__title">고액 당첨 시 주의</h2>
          </div>
          <ul className="gd__list">
            {CAUTIONS.map((t) => (
              <li key={t} className="gd__item">{t}</li>
            ))}
          </ul>
        </section>
      </div>

      <ShareLikeBar />
      <AdSlot />
      <NoticeBox>
        <div>정확한 수령처와 지급 기한은 공식 안내를 확인하세요.</div>
        <div>세금은 당첨금 · 세금 · 실수령액 계산에서 볼 수 있어요.</div>
        <div>통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.</div>
      </NoticeBox>
    </div>
  )
}
