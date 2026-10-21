import { Link } from 'react-router-dom'
import Icon from '../components/ui/Icon.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import usePageTitle from '../hooks/usePageTitle.js'
import { MENU_GROUPS } from '../app/menu.js'

// 모바일·태블릿 전체 메뉴 (목업 UI-16). PC 에서 /menu 로 들어오면 같은 내용을 카드로 보여준다.
export default function FullMenuPage() {
  usePageTitle('전체 메뉴')
  return (
    <>
      <PageHeader title="전체 메뉴" tagline />
      <div className="fmenu">
        {MENU_GROUPS.map((g) => (
          <section key={g.key} className="card fmenu__group">
            <div className="fmenu__title">{g.title}</div>
            {g.items.map((i) => (
              <Link key={i.to} to={i.to} className="fmenu__item">
                <span className="fmenu__icon">
                  <Icon name={i.icon} size={18} />
                </span>
                <span className="fmenu__text">
                  <span className="fmenu__label">{i.mLabel || i.label}</span>
                  {(i.longDesc || i.desc) && g.key !== 'info' && g.key !== 'calc' && (
                    <span className="fmenu__desc">{i.longDesc || i.desc}</span>
                  )}
                </span>
                {i.badge && <span className="fmenu__badge">{i.badge}</span>}
                <Icon name="chevronRight" size={16} color="#9FB3D6" />
              </Link>
            ))}
          </section>
        ))}
      </div>
      <AdSlot />
      <NoticeBox />
    </>
  )
}
