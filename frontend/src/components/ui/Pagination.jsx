/**
 * 숫자 페이지 이동 줄: ‹ 1 2 3 4 5 ›  (한 번에 최대 5개 번호, 현재 쪽이 가운데 오도록 움직인다)
 *  - page : 현재 쪽 (서버 기준 0부터)    - totalPages : 전체 쪽 수    - onChange(쪽 번호, 0부터)
 * 쪽이 하나뿐이면 아무것도 그리지 않는다. 목록 화면(발급 내역, 이후 당첨 기록·공지 등)이 함께 쓴다.
 */
const WINDOW = 5

export default function Pagination({ page, totalPages, onChange, disabled = false }) {
  if (totalPages <= 1) return null

  const start = Math.max(0, Math.min(page - Math.floor(WINDOW / 2), totalPages - WINDOW))
  const end = Math.min(totalPages, start + WINDOW)
  const numbers = []
  for (let p = start; p < end; p++) numbers.push(p)

  return (
    <nav className="pager" aria-label="페이지 이동">
      <button
        type="button"
        className="chip pager__arrow"
        onClick={() => onChange(page - 1)}
        disabled={disabled || page === 0}
        aria-label="이전 페이지"
      >
        ‹
      </button>
      {numbers.map((p) => (
        <button
          key={p}
          type="button"
          className={`chip pager__num${p === page ? ' is-on' : ''}`}
          onClick={() => onChange(p)}
          disabled={disabled}
          aria-current={p === page ? 'page' : undefined}
          aria-label={`${p + 1}페이지`}
        >
          {p + 1}
        </button>
      ))}
      <button
        type="button"
        className="chip pager__arrow"
        onClick={() => onChange(page + 1)}
        disabled={disabled || page >= totalPages - 1}
        aria-label="다음 페이지"
      >
        ›
      </button>
    </nav>
  )
}
