import { useState } from 'react'
import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import Icon from '../components/ui/Icon.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import { EmptyState, ErrorState } from '../components/ui/StateView.jsx'
import IssueCard from '../features/issues/IssueCard.jsx'
import { fetchIssueDetail, fetchIssueList } from '../features/issues/issuesApi.js'
import useApi from '../hooks/useApi.js'
import usePageTitle from '../hooks/usePageTitle.js'
import { CODE_LENGTH, normalizeCode } from '../lib/issueCode.js'

const PAGE_SIZE = 10

/**
 * 발급 내역 조회(UI-04).
 *  1) 발급 코드로 조회: 코드만 알면 다른 기기에서 발급한 번호도 볼 수 있다.
 *  2) 이 기기의 발급 내역: 게스트 식별값으로 이 기기에서 발급한 것만 최신순으로 보여 준다. (10건씩 "더 보기")
 * 서버가 코드를 무작위로 대입하는 공격을 막으려고 조회 횟수를 제한하므로, 형식이 틀린 코드는 서버에 묻지 않고 화면에서 먼저 걸러 준다.
 * 📚 배운 내용: 폼 제출, 배열 합치기(스프레드), async/await 와 try/catch/finally
 */
export default function IssuesPage() {
  usePageTitle('발급 내역 조회')
  const list = useApi((signal) => fetchIssueList(signal, 0, PAGE_SIZE), [])

  // 코드 조회
  const [code, setCode] = useState('')
  const [finding, setFinding] = useState(false)
  const [findError, setFindError] = useState('')
  const [found, setFound] = useState(null)

  // 더 보기: 첫 쪽(list) 뒤에 이어 붙는 쪽들. page 는 마지막으로 불러온 쪽 번호
  const [extra, setExtra] = useState({ items: [], page: 0 })
  const [moreBusy, setMoreBusy] = useState(false)
  const [moreError, setMoreError] = useState('')

  async function onFind(e) {
    e.preventDefault()
    if (finding) return
    const normalized = normalizeCode(code)
    if (normalized.length !== CODE_LENGTH) {
      setFindError(`발급 코드는 ${CODE_LENGTH}자리예요. 다시 확인해 주세요.`)
      return
    }
    setFinding(true)
    setFindError('')
    try {
      setFound(await fetchIssueDetail(normalized))
    } catch (err) {
      setFound(null)
      setFindError(err?.message || '조회하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setFinding(false)
    }
  }

  async function loadMore() {
    if (moreBusy) return
    setMoreBusy(true)
    setMoreError('')
    try {
      const next = await fetchIssueList(undefined, extra.page + 1, PAGE_SIZE)
      setExtra((prev) => ({ items: [...prev.items, ...next.items], page: prev.page + 1 }))
    } catch (err) {
      setMoreError(err?.message || '더 불러오지 못했어요.')
    } finally {
      setMoreBusy(false)
    }
  }

  const first = list.data
  const items = first ? [...first.items, ...extra.items] : []
  const hasMore = !!first && extra.page + 1 < first.totalPages

  return (
    <div className="gen issues">
      <PageHeader title="발급 내역 조회" tagline>
        <p className="phead__sub only-pc">발급 코드로 조회하거나 이 기기의 내역을 볼 수 있어요</p>
      </PageHeader>

      <div className="issues__grid">
        <form className="issues__find card" onSubmit={onFind} noValidate>
          <h2 className="issues__title">발급 코드로 조회</h2>
          <div className="issues__find-row">
            <div className="gen__field issues__field">
              <input
                className="gen__input issues__input"
                type="text"
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                maxLength={20}
                placeholder="발급 코드를 입력하세요"
                aria-label="발급 코드"
                value={code}
                onChange={(e) => {
                  setCode(e.target.value)
                  setFindError('')
                }}
              />
            </div>
            <button type="submit" className="btn btn--cta issues__go" disabled={finding}>
              {finding ? <span className="spinner spinner--sm" aria-hidden="true" /> : '조회'}
            </button>
          </div>
          {findError && (
            <p className="gen__error" role="alert">
              {findError}
            </p>
          )}
          <p className="issues__find-note only-pc">
            이 기기에서 발급한 내역만 보여요. 다른 기기의 내역은 발급 코드로 조회하세요.
          </p>
        </form>

        <div className="issues__main">
          {found && (
            <section className="issues__section" aria-label="조회 결과">
              <div className="issues__section-head">
                <h2 className="issues__title">조회 결과</h2>
                <button type="button" className="chip chip--sm" onClick={() => setFound(null)}>
                  <Icon name="close" size={14} />
                  닫기
                </button>
              </div>
              <IssueCard
                item={found}
                games={found.games.map((g) => ({ order: g.gameOrder, numbers: g.numbers }))}
              />
            </section>
          )}

          <section className="issues__section" aria-label="이 기기의 발급 내역">
            <h2 className="issues__title">이 기기의 발급 내역</h2>

            {list.loading && (
              <>
                <div className="skeleton issues__skeleton" aria-busy="true" />
                <div className="skeleton issues__skeleton" />
              </>
            )}
            {list.error && (
              <div className="card">
                <ErrorState error={list.error} onRetry={list.reload} />
              </div>
            )}
            {first && items.length === 0 && (
              <div className="card">
                <EmptyState title="이 기기에서 발급한 번호가 없어요">
                  <p>번호를 만들고 발급하면 여기에 쌓여요.</p>
                  <Link to="/generate" className="btn btn--cta">
                    <Icon name="sparkle" size={18} />
                    번호 만들러 가기
                  </Link>
                </EmptyState>
              </div>
            )}
            {items.map((it) => (
              <IssueCard
                key={it.issueCode}
                item={it}
                games={it.firstGame ? [{ order: 1, numbers: it.firstGame }] : []}
              />
            ))}

            {moreError && (
              <p className="gen__error" role="alert">
                {moreError}
              </p>
            )}
            {hasMore && (
              <button type="button" className="btn btn--ghost issues__more" onClick={loadMore} disabled={moreBusy}>
                {moreBusy ? '불러오는 중' : '더 보기'}
              </button>
            )}
          </section>
        </div>
      </div>

      <div className="only-pc">
        <AdSlot />
      </div>
      <NoticeBox>
        <div>
          통계 기반 생성 번호입니다. 참고만 하세요. 당첨을 보장하지 않습니다.
          <span className="hide-pc">
            <br />
            이 기기에서 발급한 내역만 보여요. 다른 기기의 내역은 발급 코드로 조회하세요.
          </span>
        </div>
      </NoticeBox>
    </div>
  )
}
