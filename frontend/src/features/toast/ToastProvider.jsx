import { useCallback, useRef, useState } from 'react'
import { ToastContext } from './toastContext.js'

const SHOW_MS = 2800

export default function ToastProvider({ children }) {
  const [items, setItems] = useState([])
  const seq = useRef(0)

  const show = useCallback((message) => {
    const id = ++seq.current
    setItems((list) => [...list.slice(-2), { id, message }]) // 최대 3개까지만 쌓는다
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), SHOW_MS)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toast-wrap" role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className="toast">
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
