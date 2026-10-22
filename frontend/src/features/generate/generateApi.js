import { api } from '../../api/http.js'

// 번호 생성 화면들(UI-02, 02B, 03, 03B)이 쓰는 서버 기능 모음.

// 한 번에 만들 수 있는 최대 게임 수. 서버 설정값(generation.max-games, 지금 50)과 같게 맞춰 둔다.
// 서버가 최종 검사를 하므로 이 값은 "화면에서 미리 알려 주기"용이다. 서버 설정을 바꾸면 여기도 고친다.
export const MAX_GAMES = 50
export const DEFAULT_GAMES = 10

/** API-43 현재 번호 생성 대상 회차: { targetRound, drawDate, basisRound, nextSwitchAt, statStatus, statStatusLabel } */
export { fetchCurrentRound } from '../home/homeApi.js'

/**
 * API-03 통계 번호 생성 (임시 결과, 아직 저장되지 않음)
 * 보내는 값: { gameCount, patternCode }  (patternCode 는 비워도 된다)
 * 받는 값: { confirmToken, ttlMinutes, expiresAt, games[[6개]...], patternCode, patternName, targetRound, drawDate, basisRound }
 */
export const generateNumbers = ({ gameCount, patternCode }) =>
  api.post('/api/numbers/generate', { gameCount, patternCode: patternCode || null })
