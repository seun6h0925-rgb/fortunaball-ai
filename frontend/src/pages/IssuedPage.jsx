import { useState } from 'react'
import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { EmptyState } from '../components/ui/StateView.jsx'
import LikeButton from '../features/like/LikeButton.jsx'
import ShareButton from '../features/like/ShareButton.jsx'
import { useToast } from '../features/toast/toastContext.js'
import GameList from '../features/generate/GameList.jsx'
import { gameLabel } from '../features/generate/gameLabel.js'
import MakeTabs from '../features/generate/MakeTabs.jsx'
import { loadIssued } from '../features/generate/generateStore.js'
import usePageTitle from '../hooks/usePageTitle.js'
import { groupCode } from '../lib/issueCode.js'

/**
 * 번호 발급 완료(UI-03B).
 * 발급이 끝난 번호와 발급 코드를 보여 준다. 결과 화면(UI-03)이 발급 성공 직후 브라우저에 넣어 둔 값을 읽는다.
 *  - 같은 조합이 이미 3번 발급돼 서버가 바꿔 준 게임이 있으면 안내 상자를 보여 준다.
 *  - 발급 코드는 다른 기기에서 조회할 때 필요하므로 [코드 복사]를 둔다.
 * 📚 배운 내용: navigator.clipboard(복사), 조건부 그리기, 배열 map/filter
 */
export default function IssuedPage() {
  usePageTitle('번호 발급 완료')
  const toast = useToast()
  const [issued] = useState(() => loadIssued())

  if (!issued) {
    return (
      <div className="gen">
        <PageHeader title="번호 발급 완료" actions={false} />
        <div className="card">
          <EmptyState title="방금 발급한 번호가 없어요">
            <p>발급한 번호는 발급 내역에서 코드로 조회할 수 있어요.</p>
            <Link to="/issues" className="btn btn--cta">
              발급 내역 보기
            </Link>
          </EmptyState>
        </div>
      </div>
    )
  }

  const { issueCode, targetRound, gameCount, games, replacedGames } = issued
  const numbers = [...games].sort((a, b) => a.gameOrder - b.gameOrder).map((g) => g.numbers)
  const summary = `${targetRound}회 · ${gameCount}게임 발급됨`
  const replacedNames = replacedGames.map((g) => gameLabel(g.gameOrder - 1))

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(issueCode)
      toast('발급 코드를 복사했어요.')
    } catch {
      toast('복사하지 못했어요. 코드를 길게 눌러 직접 복사해 주세요.')
    }
  }

  return (
    <div className="gen res">
      <PageHeader title="번호 발급 완료" tagline actions={false}>
        <p className="phead__sub hide-pc">{summary}</p>
        <p className="phead__sub only-pc">{targetRound}회 번호가 발급되었어요</p>
        <MakeTabs />
      </PageHeader>

      <div className="res__grid">
        <GameList games={numbers} meta={summary} />

        <aside className="res__side iss__side">
          <section className="iss__code" aria-label="발급 코드">
            <span className="sub">발급 코드</span>
            <div className="iss__code-row">
              <b className="iss__code-text">{groupCode(issueCode)}</b>
              <button type="button" className="btn btn--ghost iss__copy" onClick={copyCode}>
                코드 복사
              </button>
            </div>
            <p className="iss__code-hint">다른 기기에서 조회하려면 이 코드가 필요해요. 코드는 공유에 포함되지 않아요.</p>
          </section>

          {replacedNames.length > 0 && (
            <div className="iss__replaced" role="note">
              <b>{replacedNames.join(', ')} 번호가 바뀌었어요</b>
              <p>
                같은 회차에서 같은 번호가 이미 3번 발급돼서, {replacedNames.join(', ')}는 다른 번호로 바꿔 발급했어요.
              </p>
            </div>
          )}

          <Link to="/issues" className="btn btn--cta btn--block">
            발급 내역 보기
          </Link>

          <div className="res__row">
            <ShareButton variant="block" title="포르투나볼 AI" url={`${window.location.origin}/`} />
            <LikeButton variant="block" />
          </div>

          <div className="res__temp only-pc">
            <b>발급된 번호는 저장되었어요.</b>
            <p>추첨 후에는 발급 내역에서 검증 결과를 볼 수 있어요.</p>
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
            발급된 번호는 저장되었어요. 추첨 후에는 발급 내역에서 검증 결과를 볼 수 있어요.
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
