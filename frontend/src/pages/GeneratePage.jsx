import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { ErrorState } from '../components/ui/StateView.jsx'
import MakeTabs from '../features/generate/MakeTabs.jsx'
import {
  DEFAULT_GAMES,
  MAX_GAMES,
  fetchCurrentRound,
  generateNumbers,
} from '../features/generate/generateApi.js'
import { saveGenerated } from '../features/generate/generateStore.js'
import { PATTERN_NAMES } from '../features/generate/patterns.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'
import { formatDrawDate } from '../lib/format.js'

/**
 * 통계 번호 생성(UI-02).
 * 대상 회차를 보여 주고, 게임 수를 받아 서버에 번호 생성을 요청한다. 성공하면 결과 화면(UI-03)으로 간다.
 *  - 대상 회차·기준 회차·추첨일은 서버가 계산한 값을 그대로 보여 준다. (화면이 회차를 정하지 않는다)
 *  - 패턴 모아보기에서 넘어오면 주소에 ?pattern=HOT_NUMBER 처럼 패턴이 붙어 있고, 그 패턴으로 만든다.
 * 📚 배운 내용: useSearchParams(주소의 ?값 읽기), useNavigate(코드로 화면 이동), 폼 제출(onSubmit)
 */
export default function GeneratePage() {
  usePageTitle('통계 번호 생성')
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const current = useApi(fetchCurrentRound, [])

  // 알 수 없는 패턴 코드가 주소에 붙어 있으면 무시한다
  const rawPattern = params.get('pattern')
  const patternCode = rawPattern && PATTERN_NAMES[rawPattern] ? rawPattern : null

  const [count, setCount] = useState(String(DEFAULT_GAMES))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  function onChange(e) {
    // 숫자만, 두 자리까지
    setCount(e.target.value.replace(/\D/g, '').slice(0, 2))
    setError('')
  }

  function clearPattern() {
    const next = new URLSearchParams(params)
    next.delete('pattern')
    setParams(next, { replace: true })
  }

  async function onSubmit(e) {
    e.preventDefault()
    if (busy) return
    const n = Number(count)
    if (!count || n < 1 || n > MAX_GAMES) {
      setError(`게임 수는 1~${MAX_GAMES} 사이로 입력해 주세요.`)
      return
    }
    setBusy(true)
    setError('')
    try {
      const data = await generateNumbers({ gameCount: n, patternCode })
      saveGenerated(data)
      navigate('/generate/result')
    } catch (err) {
      // 서버가 준 안내 문구를 그대로 보여 준다. (예: 패턴 조건이 안 맞음, 요청이 너무 잦음)
      setError(err?.message || '번호를 만들지 못했어요. 잠시 후 다시 시도해 주세요.')
      setBusy(false)
    }
  }

  return (
    <div className="gen">
      <PageHeader title="통계 번호 생성" tagline>
        <p className="phead__sub only-pc">대상 회차와 게임 수를 확인하고 번호를 만들어요</p>
        <MakeTabs />
      </PageHeader>

      <div className="gen__grid">
        <TargetCard q={current} />

        <form className="gen__count card" onSubmit={onSubmit} noValidate>
          <label className="gen__label" htmlFor="gen-count">
            게임 수
          </label>
          <div className="gen__field">
            <input
              id="gen-count"
              className="gen__input"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              value={count}
              onChange={onChange}
              aria-describedby="gen-hint"
              aria-invalid={error ? 'true' : undefined}
            />
            <span className="gen__unit">게임</span>
          </div>
          <p id="gen-hint" className="gen__hint">
            한 번에 1~{MAX_GAMES}게임까지 만들 수 있어요
          </p>

          {patternCode && (
            <div className="gen__pattern">
              <span className="sub">적용 패턴</span>
              <button type="button" className="chip chip--sm is-on" onClick={clearPattern} aria-label={`${PATTERN_NAMES[patternCode]} 패턴 빼기`}>
                {PATTERN_NAMES[patternCode]}
                <Icon name="close" size={14} />
              </button>
            </div>
          )}

          {error && (
            <p className="gen__error" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="btn btn--cta btn--block" disabled={busy}>
            {busy ? (
              <>
                <span className="spinner spinner--sm" aria-hidden="true" />
                번호를 만드는 중이에요
              </>
            ) : (
              <>
                <Icon name="sparkle" size={20} />
                번호 생성하기
              </>
            )}
          </button>
        </form>
      </div>

      <AdSlot />
      <NoticeBox />
    </div>
  )
}

/** 대상 회차 카드. 서버(API-43)가 계산한 값만 보여 준다. 실패해도 아래 번호 생성은 그대로 쓸 수 있다. */
function TargetCard({ q }) {
  if (q.loading) {
    return (
      <section className="card card--hero gen__target" aria-busy="true">
        <div className="skeleton" style={{ height: 18, width: '30%' }} />
        <div className="skeleton" style={{ height: 52, width: '45%', marginTop: 12 }} />
        <div className="skeleton" style={{ height: 18, marginTop: 28 }} />
        <div className="skeleton" style={{ height: 44, marginTop: 18 }} />
      </section>
    )
  }
  if (q.error) {
    return (
      <section className="card card--hero gen__target">
        <ErrorState error={q.error} onRetry={q.reload} />
      </section>
    )
  }

  const c = q.data
  return (
    <section className="card card--hero gen__target">
      <div className="gen__target-top">
        <div>
          <div className="sub">대상 회차</div>
          <div className="gen__round">{c.targetRound}회</div>
        </div>
        <div className="gen__date">
          <div className="sub">추첨 예정일</div>
          <b>{formatDrawDate(c.drawDate)}</b>
        </div>
      </div>
      <div className="hr" />
      <div className="gen__basis">
        <span className="sub">분석 기준 회차</span>
        <b>{c.basisRound}회까지</b>
      </div>
      <p className="gen__explain">토요일 20:00부터 요청하는 번호는 다음 회차 기준으로 생성돼요.</p>
    </section>
  )
}
