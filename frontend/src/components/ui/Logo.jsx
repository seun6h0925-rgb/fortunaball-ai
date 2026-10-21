import { Link } from 'react-router-dom'
import logoImg from '../../assets/logo.png'

// 로고 그림 자체에 글자 바깥 흰 띠(외곽선)가 구워져 있어서
// 어두운 상단바와 밝은 모바일 헤더 어디에서나 같은 그림을 그대로 쓴다.
export default function Logo() {
  return (
    <Link to="/" className="logo" aria-label="포르투나볼 AI 홈">
      <img src={logoImg} alt="포르투나볼 AI" className="logo__img" />
    </Link>
  )
}
