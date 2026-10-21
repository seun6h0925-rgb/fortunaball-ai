import { ApiError } from '../../api/http.js'

// 불러오는 중 / 오류 / 비어 있음을 모든 화면에서 같은 모양으로 보여준다.
export function Loading({ label = '불러오는 중이에요' }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <div className="spinner" />
      {label}
    </div>
  )
}

export function ErrorState({ error, onRetry }) {
  const message = error instanceof ApiError || error?.message ? error.message : '일시적인 오류가 발생했어요.'
  return (
    <div className="state" role="alert">
      <div className="state__title">불러오지 못했어요</div>
      <div>{message}</div>
      {error?.traceId && <div className="state__trace">추적번호 {error.traceId}</div>}
      {onRetry && (
        <button type="button" className="btn btn--ghost" onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title = '아직 내용이 없어요', children }) {
  return (
    <div className="state">
      <div className="state__title">{title}</div>
      {children}
    </div>
  )
}
