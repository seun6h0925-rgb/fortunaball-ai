import { useEffect } from 'react'

// 브라우저 탭 제목을 "화면 이름 · 포르투나볼 AI" 로 바꾼다.
export default function usePageTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} · 포르투나볼 AI` : '포르투나볼 AI'
  }, [title])
}
