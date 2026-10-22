import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import Icon from '../../components/ui/Icon.jsx'
import { useAdminAuth } from '../../features/admin/adminAuthContext.js'

export default function AdminLoginPage() {
  const { status, login } = useAdminAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const to = location.state?.from || '/admin'

  if (status === 'in') return <Navigate to={to} replace />

  const onSubmit = async (e) => {
    e.preventDefault()
    if (busy) return
    if (!loginId.trim() || !password) {
      setError('아이디와 비밀번호를 입력해 주세요.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await login(loginId.trim(), password)
      navigate(to, { replace: true })
    } catch (err) {
      setError(err?.status === 401 ? '아이디 또는 비밀번호가 올바르지 않아요.' : err?.message || '로그인하지 못했어요.')
      setBusy(false)
    }
  }

  return (
    <main className="adlogin">
      <form className="adlogin__card" onSubmit={onSubmit} noValidate>
        <h1 className="adlogin__brand">포르투나볼 AI</h1>
        <span className="adlogin__tag">관리자 전용</span>

        <label className="adlogin__label" htmlFor="ad-id">아이디</label>
        <input
          id="ad-id"
          className={`adlogin__input${error ? ' is-error' : ''}`}
          value={loginId}
          onChange={(e) => setLoginId(e.target.value)}
          autoComplete="username"
          autoFocus
        />

        <label className="adlogin__label" htmlFor="ad-pw">비밀번호</label>
        <input
          id="ad-pw"
          type="password"
          className={`adlogin__input${error ? ' is-error' : ''}`}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        {error && (
          <p className="adlogin__error" role="alert">
            <Icon name="info" size={16} />
            {error}
          </p>
        )}

        <button type="submit" className="adlogin__btn" disabled={busy}>
          {busy ? '확인하는 중…' : '로그인'}
        </button>
        <p className="adlogin__foot">허가된 관리자만 접속할 수 있어요. 접속 기록은 남아요.</p>
      </form>
    </main>
  )
}
