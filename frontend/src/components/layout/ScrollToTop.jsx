import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

// 화면을 옮길 때마다 맨 위로 올린다. (같은 화면에서 주소 뒤 ? 값만 바뀔 때는 올리지 않는다)
export default function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}
