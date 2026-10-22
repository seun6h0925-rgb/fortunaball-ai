import { useEffect, useMemo, useRef, useState } from 'react'
import { CODE_LENGTH, COMMENT_MAX, checkPhoto, normalizeCode, registerProof } from './verifyApi.js'

/**
 * 인증 등록 입력 폼. 발급코드·사진·한마디·동의를 받아 보내고, 성공하면 onDone(결과)를 부른다.
 * 서버 거절 문구(회차 불일치·중복·낙첨 등)는 그대로 화면에 보여 준다.
 */
export default function VerifyForm({ onDone }) {
  const [code, setCode] = useState('')
  const [photo, setPhoto] = useState(null)
  const [comment, setComment] = useState('')
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const fileRef = useRef(null)

  // 미리보기 주소는 사진이 바뀌거나 화면이 닫힐 때 반드시 반납한다
  const preview = useMemo(() => (photo ? URL.createObjectURL(photo) : ''), [photo])
  useEffect(() => () => preview && URL.revokeObjectURL(preview), [preview])

  function pick(e) {
    const f = e.target.files?.[0] ?? null
    const problem = f ? checkPhoto(f) : ''
    setError(problem)
    setPhoto(problem ? null : f)
    if (problem) e.target.value = ''
  }

  const ready = normalizeCode(code).length === CODE_LENGTH && photo && consent && !busy

  async function onSubmit(e) {
    e.preventDefault()
    if (!ready) return
    setBusy(true)
    setError('')
    try {
      onDone(await registerProof(undefined, { photo, issueCode: code, comment }))
    } catch (err) {
      setError(err?.message || '등록하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card card--hero vfy__form" onSubmit={onSubmit} noValidate>
      <h2 className="vfy__title">인증 정보 입력</h2>

      <label className="vfy__field">
        <span>발급 코드</span>
        <input className="vfy__input" type="text" autoComplete="off" autoCapitalize="characters" spellCheck={false}
          maxLength={20} placeholder="발급 코드를 입력하세요" value={code}
          onChange={(e) => { setCode(e.target.value); setError('') }} />
      </label>

      <div className="vfy__field">
        <span>당첨 용지 사진 (QR이 잘 보이게)</span>
        <input ref={fileRef} className="vfy__file" type="file" accept="image/jpeg,image/png" onChange={pick}
          aria-label="당첨 용지 사진 선택" />
        <button type="button" className="vfy__drop" onClick={() => fileRef.current?.click()}>
          {preview ? <img src={preview} alt="선택한 용지 사진 미리보기" /> : <b>사진 선택하기</b>}
          <small>{photo ? `${photo.name} · 다시 누르면 바꿀 수 있어요` : 'JPG·PNG, 1장, 10MB 이하'}</small>
        </button>
      </div>

      <label className="vfy__field">
        <span>한마디 (선택)</span>
        <textarea className="vfy__input vfy__text" rows={3} maxLength={COMMENT_MAX}
          placeholder="당첨 소감을 남겨 보세요. 승인되면 공개돼요." value={comment}
          onChange={(e) => setComment(e.target.value)} />
        <small className="vfy__count">{comment.length}/{COMMENT_MAX}</small>
      </label>

      <label className="vfy__consent">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>사진 저장과 검수에 동의합니다. (사진은 공개되지 않고 검수에만 쓰여요)</span>
      </label>

      {error && <p className="gen__error" role="alert">{error}</p>}
      <button type="submit" className="btn btn--cta btn--block" disabled={!ready}>
        {busy ? <span className="spinner spinner--sm" aria-hidden="true" /> : '인증 등록하기'}
      </button>
    </form>
  )
}
