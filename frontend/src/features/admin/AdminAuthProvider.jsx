import { useCallback, useEffect, useMemo, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { adminLogin, adminLogout, adminMe } from './adminApi.js'
import { AdminAuthContext } from './adminAuthContext.js'

/** 관리자 화면 전체를 감싸서 "로그인 중인 관리자"를 한곳에서 관리한다. status: loading | in | out */
export default function AdminAuthProvider() {
  const [state, setState] = useState({ status: 'loading', admin: null })

  useEffect(() => {
    const ctrl = new AbortController()
    adminMe(ctrl.signal)
      .then((admin) => setState({ status: 'in', admin }))
      .catch((e) => {
        if (e?.name !== 'AbortError') setState({ status: 'out', admin: null })
      })
    return () => ctrl.abort()
  }, [])

  const login = useCallback(async (loginId, password) => {
    const admin = await adminLogin(loginId, password)
    setState({ status: 'in', admin })
  }, [])

  const logout = useCallback(async () => {
    try {
      await adminLogout()
    } finally {
      setState({ status: 'out', admin: null })
    }
  }, [])

  const value = useMemo(() => ({ ...state, login, logout }), [state, login, logout])
  return (
    <AdminAuthContext.Provider value={value}>
      <Outlet />
    </AdminAuthContext.Provider>
  )
}
