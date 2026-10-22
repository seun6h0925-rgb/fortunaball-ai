import { useEffect, useRef } from 'react'

/**
 * 확인 창 ("정말 하시겠어요? 아니요 / 예"). 화면 가운데에 뜨고 뒤는 어두워진다.
 *  - open=false 면 아무것도 그리지 않는다.
 *  - 처음에는 "아니요"에 커서가 있어서 실수로 엔터를 쳐도 취소된다. Esc 와 바깥 클릭도 취소.
 *  - busy=true(처리 중)에는 닫히거나 다시 눌리지 않는다.
 * 삭제처럼 되돌릴 수 없는 동작 앞에 쓰는 공통 부품이다.
 */
export default function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel = '예',
  cancelLabel = '아니요',
  busy = false,
  onConfirm,
  onCancel,
}) {
  const cancelRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    cancelRef.current?.focus()
    const onKey = (e) => {
      if (e.key === 'Escape' && !busy) onCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, busy, onCancel])

  if (!open) return null
  return (
    <div className="dialog" onClick={busy ? undefined : onCancel}>
      <div
        className="dialog__box"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="dialog-title" className="dialog__title">
          {title}
        </h2>
        <div className="dialog__body">{children}</div>
        <div className="dialog__btns">
          <button ref={cancelRef} type="button" className="btn btn--ghost" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button type="button" className="btn btn--cta dialog__ok" onClick={onConfirm} disabled={busy}>
            {busy ? <span className="spinner spinner--sm" aria-hidden="true" /> : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
