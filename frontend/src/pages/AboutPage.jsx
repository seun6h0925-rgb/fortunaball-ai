import { useState } from 'react'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import InfoTabs from '../features/info/InfoTabs.jsx'
import { useToast } from '../features/toast/toastContext.js'
import usePageTitle from '../hooks/usePageTitle.js'

// 서비스 안내 · 유의사항(UI-14). 고정 안내 화면이라 글을 이 파일에 둔다. (바꿀 때는 아래 SECTIONS 만 고친다)
//  - 글 덩어리(blocks): { p: '문장' } 한 문장 한 줄 / { h: '소제목' } / { ul: ['항목', ...] }
//  - o: 모바일·태블릿에서 위에서부터의 순서, po: PC 에서의 순서 (PC 는 두 칸씩 짝을 지어 같은 높이로 놓인다)
//  - wide: PC 에서 두 칸을 모두 차지하는 카드, open: 처음부터 펼쳐 둘 카드 (소개만 펼침, 나머지는 접어 둔다)
//  - 내용은 서버의 안내 글(content_page 시드)과 같은 뜻으로 맞춰 두었다. 한쪽을 고치면 다른 쪽도 확인한다.

const SECTIONS = [
  { key: 'intro', title: '서비스 소개', wide: true, o: 1, po: 1, open: true, blocks: [
    { p: '포르투나볼 AI는 역대 로또 데이터를 바탕으로 번호 조합, 통계, 회차 정보, 당첨 기록, 판매점 정보를 보여 주는 통계 기반 로또 정보 서비스예요.' },
    { p: '번호를 맞혀 주는 서비스가 아니라, 과거 데이터를 쉽게 살펴볼 수 있게 도와주는 서비스예요.' },
    { p: '번호는 서버의 통계 알고리즘으로만 만들고, AI는 설명을 도와줘요.' },
  ] },
  { key: 'usage', title: '사용법', o: 2, po: 2, blocks: [
    { p: '회원가입 없이 바로 이용할 수 있어요.' },
    { h: '메뉴별 사용법' },
    { ul: [
      '통계 번호 생성: 원하는 패턴을 고르면 번호 조합이 나와요. 마음에 들면 발급하기를 눌러 발급코드를 받으세요.',
      'AI 로또 애널리스트: 궁금한 점을 적으면 통계를 찾아 설명해 줘요. 번호 생성과 관련 없는 이야기는 답하지 않아요.',
      'AI 로또 꿈풀이: 꿈 내용을 적으면 상징을 풀이해요. 재미와 참고를 위한 풀이예요.',
      '로또 데이터 통계 분석: 기간을 고르면 번호별, 패턴별 통계를 볼 수 있어요.',
      '회차별 당첨번호: 회차를 고르면 당첨번호와 등수별 당첨금을 보여 줘요. 내 용지 QR로 당첨 여부도 확인할 수 있어요.',
      '판매점 지도: 지역을 검색하거나, 위치 사용에 동의하면 내 주변 판매점을 찾을 수 있어요.',
    ] },
  ] },
  { key: 'ai', title: 'AI 이용 유의사항', o: 3, po: 3, blocks: [
    { h: 'AI가 하는 일' },
    { ul: ['통계 결과를 쉽게 설명해요.', '질문이 어떤 종류인지 분류하고, 필요한 통계를 찾아 와요.', '꿈 속 상징을 풀이해요.'] },
    { h: 'AI가 하지 않는 일' },
    { ul: [
      '당첨 번호를 예측하지 않아요.',
      '당첨을 보장하거나, 당첨 가능성이 높아진다고 말하지 않아요.',
      '번호 생성과 관련 없는 이야기는 답하지 않아요. 이때는 횟수가 차감되지 않아요.',
    ] },
    { h: '알아 두세요' },
    { ul: [
      'AI 답변은 500자 이내이며, 틀리거나 부족할 수 있어요. 숫자와 사실은 회차별 당첨번호 조회 등으로 다시 확인해 주세요.',
      '통계와 AI 답변은 과거 데이터를 바탕으로 해요. 다음 추첨의 결과를 알려 주지 않아요.',
    ] },
  ] },
  { key: 'cost', title: '비용 · 후원 안내', o: 4, po: 4, blocks: [
    { h: '무료로 쓸 수 있는 기능' },
    { p: '번호 생성, 통계 조회, 회차별 당첨번호, 당첨금 계산, 수령 가이드, 판매점 지도는 모두 무료예요.' },
    { h: 'AI 기능은 하루 무료 횟수가 있어요' },
    { ul: [
      '한 사람(게스트 식별값)당 하루 5회, 같은 접속지(IP)당 하루 20회가 기본이에요. 운영 상황에 따라 바뀔 수 있어요.',
      '질문 1번이 1회예요. AI가 내부에서 여러 번 확인해도 1번만 차감돼요.',
      '다시 생성은 1회가 차감되고, 근거 보기는 차감되지 않아요.',
      'AI 호출이 실패했거나, 안내 문구만 나갔거나, 한도를 넘은 경우에는 차감되지 않아요.',
      '횟수는 한국 시간 0시에 새로 채워져요. 남은 횟수는 대화창에서 볼 수 있어요.',
      '서비스 전체 이용량 한도에 도달하면 AI 기능만 잠시 멈추고, 다른 기능은 계속 이용할 수 있어요.',
    ] },
    { h: 'AI 비용과 후원' },
    { p: 'AI 호출에는 서버 비용이 들어요.' },
    { p: '유료 이용권(횟수 충전)과 광고 보고 횟수 받기는 준비 중이에요.' },
    { p: '서비스가 마음에 드셨다면 후원으로 운영을 도와주실 수 있어요.' },
  ] },
  { key: 'dream', title: '꿈풀이 안내 · 출처', o: 6, po: 5, blocks: [
    { p: 'AI 로또 꿈풀이는 재미와 참고를 위한 풀이예요. 꿈이 결과를 알려 주는 것은 아니에요.' },
    { p: '꿈 상징 풀이는 서비스가 모아 둔 꿈 상징 자료를 바탕으로 하고, 각 상징에는 참고한 출처가 함께 기록돼 있어요.' },
    { h: '주요 참고 자료' },
    { ul: [
      '한국 해몽 전통을 소개한 언론 칼럼과 동물민속학자 인터뷰',
      '한국 해몽의 길몽·흉몽 분류를 정리한 위키 자료',
      '나폴리 스모르피아, 쿠바 차라다 같은 번호와 상징 민속 자료',
    ] },
    { p: '출처의 문장은 그대로 옮기지 않고 서비스 문장으로 다시 썼어요.' },
    { p: '출처마다 신뢰도를 함께 관리해요.' },
  ] },
  { key: 'privacy', title: '개인정보 안내', o: 7, po: 6, blocks: [
    { p: '회원가입을 받지 않고, 서비스에 꼭 필요한 정보만 최소한으로 사용해요.' },
    { h: '게스트 식별값' },
    { ul: [
      '서비스가 임의로 만들어 주는 값이에요. 이름, 연락처, 계정 정보와는 연결되지 않아요.',
      '내가 만든 번호의 발급 이력 확인과 AI 기능의 하루 이용 횟수 계산에 사용해요.',
      '식별값과 번호 발급 기록(발급 번호, 회차, 당첨 판정 결과)을 저장해요.',
    ] },
    { h: '접속지(IP)' },
    { p: '하루 이용 횟수와 과도한 요청을 제한하는 데에만 사용해요.' },
    { h: '저장하지 않는 정보' },
    { ul: [
      'AI 대화 원문과 꿈 이야기 원문은 저장하지 않아요.',
      '위치 정보는 내 주변 판매점을 찾을 때만 쓰고, 저장하거나 AI에 전달하지 않아요. 직접 스위치를 켜고 동의한 경우에만 쓸 수 있어요.',
      '내 용지 확인하기의 사진은 서버로 보내지 않고, 읽은 QR 글자만 전달하며 저장하지 않아요.',
    ] },
    { h: '당첨 인증을 이용하는 경우' },
    { ul: [
      '올린 사진과 댓글을 저장하고 검수해요. 저장과 검수에 동의한 경우에만 올릴 수 있어요.',
      '사진에 이름, 주소, 연락처 같은 개인정보가 보이지 않게 해 주세요.',
    ] },
  ] },
  { key: 'noguarantee', title: '당첨 보장 불가', o: 8, po: 7, blocks: [
    { p: '통계 기반 생성 번호는 참고용이며, 당첨 확률과 무관하고 당첨을 보장하지 않아요.' },
    { ul: [
      '로또 추첨은 매 회차 독립적으로 이루어져요. 지난 회차에 어떤 번호가 많이 나왔는지는 다음 추첨의 결과를 바꾸지 않아요.',
      '이 서비스의 통계는 과거에 있었던 일을 정리한 사실이에요. 앞으로 일어날 일을 알려 주지 않아요.',
      '번호 생성, AI 분석, 꿈풀이 모두 참고용이에요.',
    ] },
  ] },
  { key: 'gambling', title: '도박 과몰입 주의', wide: true, o: 9, po: 10, fixed: true, call: true, blocks: [
    { p: '복권에 지나치게 빠지면 마음의 어려움이나 경제적인 문제로 이어질 수 있어요. 소액으로 가볍게 즐기는 정도를 지켜 주세요.' },
    { h: '이런 때는 잠시 멈춰 주세요' },
    { ul: ['구매 금액이 점점 늘어나고 있어요.', '잃은 돈을 되찾으려고 더 사고 싶어져요.', '복권 생각 때문에 일상이나 인간관계가 힘들어졌어요.'] },
    { p: '만 19세 미만은 복권을 구매할 수 없어요.' },
  ] },
  { key: 'disclaimer', title: '면책 고지', o: 10, po: 8, blocks: [
    { ul: [
      '이 서비스는 참고용 정보를 제공해요. 번호 구매, 당첨금 수령 등 모든 결정과 그 결과의 책임은 이용하는 분에게 있어요.',
      '공식 당첨 결과는 동행복권 기준이에요. 이 서비스의 당첨번호, 당첨금, 판정 결과는 공식 결과와 다를 수 있어요.',
      '당첨금 세금 계산은 참고용이에요. 실제 세금과 수령액은 공식 기준에 따라 달라질 수 있어요.',
      '판매점 정보(위치, 영업 여부, 당첨 이력)는 실제와 다를 수 있어요. 가기 전에 한 번 확인해 주세요.',
      'AI 답변과 꿈풀이는 정확성을 보장하지 않아요.',
      '서비스는 점검이나 외부 서비스 문제로 일시적으로 멈출 수 있어요.',
    ] },
  ] },
]

function Blocks({ blocks }) {
  return blocks.map((b, i) => {
    if (b.p) return <p key={i}>{b.p}</p>
    if (b.h) return <h3 key={i} className="ab__h">{b.h}</h3>
    return <ul key={i} className="ab__ul">{b.ul.map((t) => <li key={t}>{t}</li>)}</ul>
  })
}

function Section({ s }) {
  const [open, setOpen] = useState(!!s.open)
  const blocks = <Blocks blocks={s.blocks} />
  // 도박 과몰입 주의: PC 에서는 왼쪽에 안내 글, 오른쪽에 상담 전화 칸으로 나눠 가로로 넓게 보여 준다
  const body = s.call ? (
    <div className="ab__body ab__body--split" id={`ab-${s.key}`}>
      <div className="ab__split-l">{blocks}</div>
      <div className="ab__split-r">
        <p>도박문제 상담은 한국도박문제예방치유원 1336에서 받을 수 있어요.</p>
        <a className="btn btn--cta btn--block ab__btn" href="tel:1336">한국도박문제예방치유원 1336</a>
        <p>동행복권 동행클린센터는 080-800-0501이에요. (평일 오전 9시~오후 6시, 주말·공휴일 휴무)</p>
      </div>
    </div>
  ) : (
    <div className="ab__body" id={`ab-${s.key}`}>{blocks}</div>
  )
  return (
    <section
      className={`card card--hero ab__card${s.wide ? ' ab__card--wide' : ''}`}
      style={{ '--o': s.o, '--po': s.po }}
    >
      {s.fixed ? (
        <>
          <h2 className="ab__title ab__title--fixed">{s.title}</h2>
          {body}
        </>
      ) : (
        <>
          <button type="button" className="ab__head" aria-expanded={open} aria-controls={`ab-${s.key}`} onClick={() => setOpen((v) => !v)}>
            <h2 className="ab__title">{s.title}</h2>
            <Icon name="chevronDown" size={20} className={`ab__chev${open ? ' is-open' : ''}`} />
          </button>
          {open && body}
        </>
      )}
    </section>
  )
}

// 후원하기 줄: 접힌 카드 한 장과 같은 높이로 만들어 짝을 이루는 카드와 높이를 맞춘다. 비용 · 후원 안내와 가까이(모바일) 놓는다.
function SupportBar() {
  const toast = useToast()
  return (
    <section className="card card--hero ab__card ab__support" style={{ '--o': 5, '--po': 9 }}>
      <div className="ab__support-text">
        <h2 className="ab__title">서비스가 마음에 드셨나요?</h2>
        <p>후원으로 운영을 도와주실 수 있어요.</p>
      </div>
      <button type="button" className="btn btn--ghost ab__support-btn" onClick={() => toast('후원 기능은 준비 중이에요.')}>
        후원하기
      </button>
    </section>
  )
}

/** 서비스 안내 · 유의사항(UI-14). 항목을 눌러 펼치고 접는다. 모바일은 한 줄로 쌓이고, PC·넓은 태블릿은 두 칸씩 짝을 이뤄 높이가 맞게 놓인다. */
export default function AboutPage() {
  usePageTitle('서비스 안내 · 유의사항')
  return (
    <div className="ab">
      <PageHeader title="서비스 안내 · 유의사항" tagline>
        <p className="phead__sub">이용 전 꼭 확인해 주세요</p>
        <InfoTabs group="help" />
      </PageHeader>

      <div className="ab__cols">
        {SECTIONS.map((s) => <Section key={s.key} s={s} />)}
        <SupportBar />
      </div>

      <AdSlot />
      <NoticeBox />
    </div>
  )
}
