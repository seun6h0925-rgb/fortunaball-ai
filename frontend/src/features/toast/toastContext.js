import { createContext, useContext } from 'react'

// 화면 어디서든 useToast()(message) 로 짧은 알림을 띄운다.
export const ToastContext = createContext(() => {})
export const useToast = () => useContext(ToastContext)
