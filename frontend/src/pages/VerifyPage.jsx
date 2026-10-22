import { useState } from 'react'
import { Link } from 'react-router-dom'
import PageHeader, { ShareLikeBar } from '../components/layout/PageHeader.jsx'
import AdSlot from '../components/ui/AdSlot.jsx'
import NoticeBox from '../components/ui/NoticeBox.jsx'
import VerifyForm from '../features/verify/VerifyForm.jsx'
import usePageTitle from '../hooks/usePageTitle.js'

const STEPS = [
  ['발급 코드 입력', '번호를 발급받을 때 받은 코드예요.'],
  ['당첨 용지 사진 올리기', '용지의 QR이 잘 보이게 찍어 주세요.'],
  ['검수 후 공개', '확인되면 우리 서비스 당첨 기록에 공개돼요.'],
]

/**
 * 익명 당첨 인증 등록(UI-10). 로그인 없이 발급코드와 당첨 용지 사진으로 인증한다(API-16).
 * 회차·등수는 입력하지 않는다. 서버가 용지 QR 과 공식 당첨번호를 비교해서 정한다.
 */
export default function VerifyPage() {
  usePageTitle('익명 당첨 인증 등록')
  const [done, setDone] = useState(null)

  return (
    <div className="vfy">
      <PageHeader title="익명 당첨 인증 등록" tagline share>
        <p className="phead__sub only-pc">당첨 용지 사진으로 인증하면 우리 서비스 당첨 기록에 공개돼요</p>
      </PageHeader>

      <div className="vfy__grid">
        {done ? (
          <section className="card card--hero vfy__done" aria-live="polite">
            <h2 className="vfy__title">인증을 등록했어요</h2>
            <p>{done.message}</p>
            <span className="chip chip--sm is-on">검수 대기</span>
            <Link to="/winners" className="btn btn--cta btn--block">우리 서비스 당첨 기록 보기</Link>
            <button type="button" className="btn btn--ghost btn--block" onClick={() => setDone(null)}>
              다른 인증 등록하기
            </button>
          </section>
        ) : (
          <VerifyForm onDone={setDone} />
        )}

        <section className="card card--hero vfy__steps" aria-label="인증 순서">
          <h2 className="vfy__title">인증 순서</h2>
          <ol>
            {STEPS.map(([t, d], i) => (
              <li key={t}>
                <b>{i + 1}</b>
                <div><strong>{t}</strong><p>{d}</p></div>
              </li>
            ))}
          </ol>
        </section>
      </div>

      <ShareLikeBar />
      <AdSlot />
      <NoticeBox>
        <div>이 서비스에서 발급한 번호가 당첨된 용지만 인증할 수 있어요.</div>
        <div>같은 발급코드·같은 용지는 한 번만 등록돼요. (반려되면 다시 등록할 수 있어요)</div>
        <div>사진은 공개되지 않고, 검수에만 쓰여요.</div>
      </NoticeBox>
    </div>
  )
}
