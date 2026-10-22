// 관리자 왼쪽 메뉴 (목업 AD-02 순서). 만든 화면은 App.jsx 의 ADMIN_BUILT 에도 적는다.
export const ADMIN_MENU = [
  { group: '운영', items: [
    { path: '/admin', label: '대시보드', icon: 'pie' },
    { path: '/admin/stats', label: '서비스 통계', icon: 'bars' },
    { path: '/admin/system', label: '시스템 운영', icon: 'list' },
  ] },
  { group: '회차·검증', items: [
    { path: '/admin/draws', label: '회차 데이터', icon: 'doc' },
    { path: '/admin/issues', label: '발급 기록', icon: 'doc' },
    { path: '/admin/verify', label: '당첨 검증', icon: 'check' },
    { path: '/admin/winners', label: '당첨 기록', icon: 'trophy' },
    { path: '/admin/photos', label: '인증 검수', icon: 'image' },
  ] },
  { group: '데이터', items: [
    { path: '/admin/stores', label: '판매점', icon: 'pin' },
    { path: '/admin/dreams', label: '꿈 상징', icon: 'moon' },
  ] },
  { group: 'AI · 콘텐츠', items: [
    { path: '/admin/ai', label: 'AI 운영', icon: 'sparkle' },
    { path: '/admin/policy', label: 'AI 입력 정책', icon: 'info' },
    { path: '/admin/contents', label: '콘텐츠', icon: 'book' },
  ] },
]
