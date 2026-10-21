// 메뉴 구성의 단 하나뿐인 원본. PC 상단 메뉴, 모바일 전체 메뉴(UI-16), 하단 바가 모두 이 파일을 쓴다.
// 메뉴 이름·설명·순서를 바꿀 때는 여기만 고친다.
export const MENU_GROUPS = [
  {
    key: 'make',
    title: '번호 만들기',
    icon: 'moon',
    items: [
      { to: '/generate', label: '통계 번호 생성', desc: '통계로 번호 만들기', longDesc: '대상 회차 번호를 통계로 만들어요', icon: 'sparkle' },
      { to: '/generate/patterns', label: '패턴 모아보기', desc: '패턴을 비교하고 생성', longDesc: '패턴을 비교하고 골라서 만들어요', icon: 'sparkle' },
      { to: '/analyst', label: 'AI 애널리스트', desc: '번호 분석 · 질문', longDesc: '대화로 분석하고 번호를 받아요', icon: 'bars' },
      { to: '/dream', label: '꿈풀이 번호', desc: '꿈으로 번호 얻기', longDesc: '꿈 내용으로 참고 번호를 만들어요', icon: 'moon' },
    ],
  },
  {
    key: 'info',
    title: '당첨 정보',
    icon: 'bars',
    items: [
      { to: '/draws', label: '회차별 당첨번호', desc: '회차별 결과', icon: 'list' },
      { to: '/stats', label: '통계 분석', mLabel: '로또 데이터 통계 분석', desc: '핫넘버·센터 존 등', icon: 'pie' },
      { to: '/winners', label: '우리 서비스 당첨 기록', desc: '공개된 당첨 기록', icon: 'trophy' },
      { to: '/map', label: '명당 지도', mLabel: '로또 명당 지도', desc: '판매점 찾기', icon: 'pin' },
    ],
  },
  {
    key: 'calc',
    title: '계산 · 가이드',
    icon: 'check',
    items: [
      { to: '/payout', label: '당첨금 · 세금', mLabel: '당첨금 · 세금 · 실수령액', desc: '실수령액 계산', icon: 'doc' },
      { to: '/guide/payout', label: '수령 가이드', mLabel: '당첨금 수령 가이드', desc: '받는 방법', icon: 'book' },
    ],
  },
  {
    key: 'mine',
    title: '내 기록',
    icon: 'image',
    items: [
      { to: '/issues', label: '발급 내역 조회', desc: '발급코드로 조회', longDesc: '이 기기에서 발급한 번호', icon: 'doc' },
    ],
  },
  {
    key: 'help',
    title: '안내 · 공지',
    icon: 'info',
    items: [
      { to: '/about', label: '서비스 안내', mLabel: '서비스 안내 · 유의사항', desc: '이용 전 확인', icon: 'info' },
      { to: '/notices', label: '공지사항', desc: '새 소식', icon: 'bell', badge: 'N' },
    ],
  },
]

// 모바일·태블릿 하단 바 (목업: 홈·생성·분석·통계·내역). match 는 "이 주소들이면 이 탭이 켜진다".
export const BOTTOM_TABS = [
  { key: 'home', label: '홈', to: '/', icon: 'home', match: ['/'], exact: true },
  { key: 'make', label: '생성', to: '/generate', icon: 'sparkle', match: ['/generate'] },
  { key: 'analyze', label: '분석', to: '/analyst', icon: 'bars', match: ['/analyst', '/dream'] },
  { key: 'stats', label: '통계', to: '/stats', icon: 'pie', match: ['/stats', '/draws'] },
  { key: 'history', label: '내역', to: '/issues', icon: 'doc', match: ['/issues', '/winners', '/verify'] },
]

export function isPathActive(pathname, target, exact = false) {
  if (exact || target === '/') return pathname === target
  return pathname === target || pathname.startsWith(target + '/')
}
