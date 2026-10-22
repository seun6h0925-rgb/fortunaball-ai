import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import LottoBall from '../components/ui/LottoBall.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { EmptyState } from '../components/ui/StateView.jsx'
import LikeButton from '../features/like/LikeButton.jsx'
import ShareButton from '../features/like/ShareButton.jsx'
import MakeTabs from '../features/generate/MakeTabs.jsx'
import { confirmIssue, generateNumbers } from '../features/generate/generateApi.js'
import { clearGenerated, loadGenerated, saveGenerated, saveIssued } from '../features/generate/generateStore.js'
import usePageTitle from '../hooks/usePageTitle.js'

/** 게임 이름: A, B, C ... Z, 그 뒤로는 27, 28 ... (게임은 최대 50개까지 만들 수 있다) */
function gameLabel(index) {
  return index < 26 ? `게임 ${String.fromCharCode(65 + index)}` : `게임 ${index + 1}`
}

/**
 * 번호 생성 결과(UI-03).
 * 방금 만든 번호(아직 저장 전, 서버가 30분만 보관)를 보여 주고, [발급하기]로 확정하거나 [다시 생성]한다.
 *  - 결과는 생성 화면이 브라우저에 넣어 둔 값을 읽는다. 없거나 30분이 지났으면 "만든 번호가 없어요"를 보여 준다.
 *  - 발급하기: 번호는 다시 보내지 않고 임시 확정 토큰만 보낸다. (서버가 자기가 들고 있는 번호로 확정)
 *  - 다시 생성: 같은 게임 수·같은 패턴으로 바로 새로 만든다. (이전 결과는 서버에서 버려진다)
 * 📚 배운 내용: useEffect 정리 함수(타이머 해제), 조건부 화면 그리기, async/await 와 try/catch
 */
export default function ResultPage() {
  usePageTitle('번호 생성 결과')
  const navigate = useNavigate()
  const [data, setData] = useState(() => loadGenerated())
  const [expired, setExpired] = useState(false)
  const [busy, setBusy] = useState(null) // null | 'issue' | 'regen'
  const [error, setError] = useState('')

  // 서버 보관 시간(30분)이 끝나는 순간 화면도 "만료"로 바꾼다
  const expiresAt = data?.expiresAt
  useEffect(() => {
    if (!expiresAt) return undefined
    const wait = Math.max(Date.parse(expiresAt) - Date.now(), 0) + 50
    const timer = setTimeout(() => setExpired(true), wait)
    return () => clearTimeout(timer)
  }, [expiresAt])

  if (!data || expired) {
    return (
      <div className="gen">
        <PageHeader title="번호 생성 결과" actions={false} />
        <div className="card">
          <EmptyState title={expired ? '결과가 만료됐어요' : '만든 번호가 없어요'}>
            <p>
              {expired
                ? `발급하지 않은 번호는 ${data.ttlMinutes}분이 지나면 사라져요.`
                : '번호를 먼저 만들어 주세요.'}
            </p>
            <Link to="/generate" className="btn btn--cta">
              <Icon name="sparkle" size={18} />
              번호 만들러 가기
            </Link>
          </EmptyState>
        </div>
      </div>
    )
  }

  const { games, targetRound, basisRound, patternCode, patternName, ttlMinutes } = data
  const summary = `${targetRound}회 · ${games.length}게임${patternName ? ` · ${patternName}` : ''} · 분석 기준 ${basisRound}회까지`

  async function onIssue() {
    if (busy) return
    setBusy('issue')
    setError('')
    try {
      const issued = await confirmIssue(data.confirmToken)
      saveIssued(issued)
      clearGenerated()
      navigate('/generate/issued')
    } catch (err) {
      setError(err?.message || '발급하지 못했어요. 잠시 후 다시 시도해 주세요.')
      setBusy(null)
    }
  }

  async function onRegenerate() {
    if (busy) return
    setBusy('regen')
    setError('')
    try {
      const next = await generateNumbers({ gameCount: games.length, patternCode })
      saveGenerated(next)
      setData(next)
      setExpired(false)
    } catch (err) {
      setError(err?.message || '번호를 만들지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="gen res">
      <PageHeader title="번호 생성 결과" tagline actions={false}>
        <p className="phead__sub hide-pc">{summary}</p>
        <p className="phead__sub only-pc">{targetRound}회 번호가 만들어졌어요</p>
        <MakeTabs />
      </PageHeader>

      <div className="res__grid">
        <section className="card res__games" aria-label="생성된 번호">
          <div className="res__head only-pc">
            <h2>생성된 번호</h2>
            <span className="sub">{summary}</span>
          </div>
          <ul className="res__list">
            {games.map((numbers, i) => (
              <li key={i} className="res__game">
                <span className="res__name">{gameLabel(i)}</span>
                <div className="res__balls" aria-label={`${gameLabel(i)} ${numbers.join(', ')}`}>
                  {numbers.map((n) => (
                    <LottoBall key={n} n={n} />
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </section>

        <aside className="res__side">
          <button type="button" className="btn btn--cta btn--block" onClick={onIssue} disabled={!!busy}>
            {busy === 'issue' ? (
              <>
                <span className="spinner spinner--sm" aria-hidden="true" />
                발급하는 중이에요
              </>
            ) : (
              '발급하기'
            )}
          </button>

          <div className="res__row">
            <button type="button" className="btn btn--ghost" onClick={onRegenerate} disabled={!!busy}>
              {busy === 'regen' ? (
                <>
                  <span className="spinner spinner--sm spinner--light" aria-hidden="true" />
                  만드는 중
                </>
              ) : (
                '다시 생성'
              )}
            </button>
            <Link to="/stats" className="btn btn--ghost" aria-disabled={busy ? 'true' : undefined}>
              분석 보기
            </Link>
          </div>

          <div className="res__row">
            {/* 번호는 이 기기에만 있어서 번호 주소를 보내면 빈 화면이 된다. 서비스 주소를 공유한다 */}
            <ShareButton variant="block" title="포르투나볼 AI" url={`${window.location.origin}/`} />
            <LikeButton variant="block" />
          </div>

          {error && (
            <p className="gen__error" role="alert">
              {error}
            </p>
          )}

          {/* PC: 오른쪽 카드 안의 안내 / 모바일·태블릿: 아래 별도 상자 */}
          <div className="res__temp only-pc">
            <b>아직 저장된 번호가 아니에요.</b>
            <p>
              발급하기를 누르면 발급 코드와 함께 확정 저장되고, 누르지 않으면 이 결과는 {ttlMinutes}분 뒤 사라져요.
            </p>
          </div>
        </aside>
      </div>

      <div className="hide-pc">
        <NoticeBox>
          <div>
            통계 기반 생성 번호입니다. 참고만 하세요.
            <br />
            당첨을 보장하지 않습니다.
            <br />
            아직 저장된 번호가 아니에요. 발급하기를 누르면 발급 코드와 함께 확정 저장되고,
            <br />
            누르지 않으면 이 결과는 {ttlMinutes}분 뒤 사라져요.
          </div>
        </NoticeBox>
      </div>
      <div className="only-pc">
        <AdSlot />
      </div>
      <div className="only-pc">
        <NoticeBox />
      </div>
    </div>
  )
}
