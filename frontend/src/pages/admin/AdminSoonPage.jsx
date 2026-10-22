import { ADMIN_MENU } from '../../features/admin/adminMenu.js'
import { useLocation } from 'react-router-dom'

/** 아직 만들지 않은 관리자 화면 자리 */
export default function AdminSoonPage() {
  const { pathname } = useLocation()
  const item = ADMIN_MENU.flatMap((g) => g.items).find((i) => i.path === pathname)
  return (
    <div className="adsoon">
      <h1>{item?.label || '관리자'}</h1>
      <p>이 화면은 준비 중이에요.</p>
    </div>
  )
}
