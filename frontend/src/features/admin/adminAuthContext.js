import { createContext, useContext } from 'react'

export const AdminAuthContext = createContext(null)

export function useAdminAuth() {
  const v = useContext(AdminAuthContext)
  if (!v) throw new Error('AdminAuthProvider 안에서만 사용할 수 있어요.')
  return v
}
